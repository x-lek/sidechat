package com.xavier.sidechat.service;

import com.xavier.sidechat.entity.Image;
import com.xavier.sidechat.entity.Post;
import com.xavier.sidechat.entity.Quote;
import com.xavier.sidechat.entity.Thread;
import com.xavier.sidechat.repository.PostRepository;
import com.xavier.sidechat.repository.ThreadRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class CrawlerService {

    @Scheduled(cron = "0 0/60 * * * ?")
    public void doProcessTop20Pages() {
        this.processTopPages(20);
    }

    private final String USER_AGENT = "Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6";

    private final PostRepository postRepository;
    private final ThreadRepository threadRepository;

    public CrawlerService(PostRepository postRepository, ThreadRepository threadRepository) {
        this.postRepository = postRepository;
        this.threadRepository = threadRepository;
    }

    SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssXXX");
    //SimpleDateFormat threadDateFormatter = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm aa");

    public void processTopPages(int pages) {
        // Crawl thread information within top X pages
        List<Thread> threadList = this.getAllThreadsInTopPages(pages);
        threadRepository.saveAll(threadList);

        // Determine if thread has new posts
        List<Post> postList = StreamSupport.stream(threadList.spliterator(), true)
                .map(this::processThread)
                .flatMap(posts -> posts.stream())
                .collect(Collectors.toList());

        log.info("Captured {} posts", postList.size());
    }

    private List<Thread> getAllThreadsInTopPages(int pages) {
        final String url = "https://forums.hardwarezone.com.sg/eat-drink-man-woman-16/index%d.html";

        List<Thread> threadList = new ArrayList<>();
        for (int i = 1; i <= pages; i++) {
            Document document = null;
            try {
                String currentPageUrl = String.format(url, i);
                document = Jsoup.connect(currentPageUrl)
                        .userAgent(USER_AGENT)
                        .get();
                log.debug("Processing page #{} at {}", i, currentPageUrl);

                Elements threadsObjects = document.select(
                        "div.structItemContainer-group.js-threadList > " +
                                    "div.structItem--thread");

                for (Element t : threadsObjects) {
                    String threadUrl = String.format("https://forums.hardwarezone.com.sg%s",
                            t.select("div.structItem-title > a")
                                    .attr("href"));

                    String threadId = threadUrl.substring(threadUrl.lastIndexOf('.')+1, threadUrl.length()-1);

                    String title = t.select("div.structItem-title").text();
                    String threadStarter = t.select(
                            "div.structItem-minor > " +
                                        "ul.structItem-parts > " +
                                        "li > a.username").text();
                    Date threadStartDate = new Date(Long.parseLong((
                                t.select("div.structItem-minor > " +
                                                "ul.structItem-parts > " +
                                                "li.structItem-startDate > a > time")
                                        .attr("data-time"))) * 1000);

                    Date threadLastModifiedDate = new Date();
                    try {
                        threadLastModifiedDate = new Date(Long.parseLong(
                                t.select("div.structItem-cell.structItem-cell--latest > a > time")
                                        .attr("data-time")) * 1000);
                    } catch (NumberFormatException ignored) {
                        ignored.printStackTrace();
                    }

                    // Get Approximated View Count of Thread
                    Long viewCount = this.normalizeViewCount(
                            t.select("div.structItem-cell.structItem-cell--meta > " +
                                        "dl.pairs.pairs--justified.structItem-minor > dd").text());

                    // Get Last Page of Thread
                    Elements pageJumpElement = t.select("div.structItem-minor >" +
                                                        "span.structItem-pageJump > a");
                    Long lastPage = 1L;
                    if(pageJumpElement.size() > 0) {
                        try {
                            lastPage = Long.parseLong(
                                    pageJumpElement.last()
                                            .attr("href")
                                            .split("page-")[1]);
                        } catch (Exception ignored) {
                            ignored.printStackTrace();
                        }
                    }

                    threadList.add(Thread.builder()
                            .id(threadId)
                            .url(threadUrl)
                            .title(title)
                            .creator(threadStarter)
                            .startDate(threadStartDate)
                            .lastModified(threadLastModifiedDate)
                            .viewCount(viewCount)
                            .lastPage(lastPage)
                            .build());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return threadList;
    }

    private Optional<Post> extractPostInfo(Element postElement, Thread thread) {
        try {
            Long id = Long.parseLong(postElement.attr("data-content").split("-")[1]);
            String author = postElement.attr("data-author");

            // 3 user extra info, 1. joined, 2. messages, 3. reaction
            Elements userExtraInfo = postElement.select("div.message-userExtras > dl");
            Long authorPostCount = Long.parseLong(
                    userExtraInfo.get(1)//??
                            .select("dd").text()
                            .replace(",", ""));

            Date publishedDate = new Date(Long.parseLong(
                        postElement.select("time").attr("data-time").trim()) * 1000);
            String publishedDateString = postElement.select("time").attr("title").trim();

            int localPostId = Integer.parseInt(
                    postElement
                            .select("ul.message-attribution-opposite.message-attribution-opposite--list > li")
                            .get(1).text().replaceAll("[#,]", ""));

            Element bodyElement = postElement.select("div.bbWrapper").first();

            String text = bodyElement.text();
            String html = bodyElement.html();

            List<Image> imageList = new ArrayList<>();
            for (Element e : bodyElement.select("div.bbImageWrapper")) {
                imageList.add(new Image(e.attr("data-src"), e.attr("title")));
            }

            List<Quote> quoteList = new ArrayList<>();
            for (Element e : bodyElement.select("blockquote")) {
                try {
                    Element quoteHeaderElement = e.select("a.bbCodeBlock-sourceJump").first();
                    String postId = quoteHeaderElement.attr("data-content-selector").replaceAll("#post-", "");
                    String quoteAuthor = quoteHeaderElement.text().replaceAll(" said:", "");
                    String quoteText = e.select("div.bbCodeBlock-content").text()
                            .replace(quoteHeaderElement.text(), "")
                            .replace("Click to expand...", "")
                            .trim();

                    // remove quote text from text body
                    text = text
                            .replace(quoteHeaderElement.text(), "")
                            .replace(quoteText, "")
                            .replace("Click to expand...", "")
                            .trim();

                    quoteList.add(new Quote(quoteAuthor, postId, quoteText));
                } catch (Exception ignored) {
                    ignored.printStackTrace();
                }
            }

            Post post = Post.builder()
                    .id(id)
                    .threadId(thread.getId())
                    .threadTitle(thread.getTitle())
                    .localPostId(localPostId)
                    .author(author)
                    .authorPostCount(authorPostCount)
                    .publishedDate(publishedDate)
                    .publishedDateString(publishedDateString)
                    .text(text)
                    .html(html)
                    .images(imageList)
                    .quotes(quoteList)
                    .build();

            return Optional.of(post);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public List<Post> processThreadPage(Thread thread, Long page) {
        List<Post> postListByPage = new ArrayList<>();

        Document document = null;
        try {
            String currentPageUrl = String.format(thread.getUrl() + "page-%d", page);
            document = Jsoup.connect(currentPageUrl)
                    .userAgent(USER_AGENT)
                    .get();
            log.debug("Processing page #{}/{} of thread {}", page, thread.getLastPage(), thread.getTitle());

            Elements postsObjects = document.select(
                    "div.block-body.js-replyNewMessageContainer > " +
                            "article.message--post");

            Stream<Element> elementStream = StreamSupport.stream(postsObjects.spliterator(), false);
            elementStream
                    .map(element -> this.extractPostInfo(element, thread))
                    .filter(optionalPost -> optionalPost.isPresent())
                    .forEach(post -> {
                        postListByPage.add(post.get());
                    });

        } catch (IOException e) {
            e.printStackTrace();
        }
        postRepository.saveAll(postListByPage);
        return postListByPage;
    }

    public List<Post> processThread(Thread thread) {
        if(thread.getLastPage()>100) {
            return new ArrayList<>();
        }
        return StreamSupport.stream(LongStream.range(1, thread.getLastPage()).spliterator(), false)
                .map(pageNum -> this.processThreadPage(thread, pageNum))
                .flatMap(posts -> posts.stream())
                .collect(Collectors.toList());
    }

    private Long getLongIgnoreLastChar(String str) { return Long.parseLong(str.substring(0, str.length() - 1)); }

    private Long normalizeViewCount(String numString) {
        return switch (numString.toLowerCase().charAt(numString.length()-1)) {
            case 'k' -> this.getLongIgnoreLastChar(numString) * 1000;
            case 'm' -> this.getLongIgnoreLastChar(numString) * 1000000;
            default -> {
                try {
                    yield Long.parseLong(numString);
                } catch (Exception pe) {
                    pe.printStackTrace();
                    yield  0L;
                }
            }
        };
    }



//    private long convertNumberStringToLong(String number) {
//        return Long.parseLong(number.replaceAll(",", ""));
//    }
//
//    private Date convertJoinedDate(String joinedDate) throws ParseException {
//        SimpleDateFormat df = new SimpleDateFormat("MMM yyyy");
//        return df.parse(joinedDate.trim());
//    }
//
//    private Date convertPostDate(String date) throws ParseException {
//        String normalizedDate = date;
//        if (date.contains("Yesterday") || date.contains("Today")) {
//            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy, ");
//            String time = date.split(",")[1].trim();
//            Calendar c = Calendar.getInstance();
//
//            if (date.contains("Yesterday")) {
//                c.add(Calendar.DATE, -1);
//            }
//
//            Date yesterday = c.getTime();
//            normalizedDate = dateFormat.format(yesterday) + time;
//        }
//        SimpleDateFormat datetimeFormat = new SimpleDateFormat("dd-MM-yyyy, hh:mm aa");
//        return datetimeFormat.parse(normalizedDate);
//    }
//
//    public void crawlThread(String url) {
//        List<Post> postList = new ArrayList<>();
//        String currentUrl = url;
//        int currentPage = 1;
//
//        // update currentPage is thread has been processed before
//        Optional<Thread> t = threadRepository.findById(url);
//        if (t.isPresent()) {
//            currentPage = t.get().getPageLastProcessed();
//        }
//
//        // update current url to page last processed
//        currentUrl = currentUrl.replace(".html", String.format("-%d.html", currentPage));
//
//        while (!currentUrl.isEmpty()) {
//            Document document = null;
//            try {
//                log.info("Processing thread URL {}", currentPage);
//                document = Jsoup.connect(currentUrl).get();
//
//                // Crawl posts in page
//                List<Post> crawledPost = this.crawlPostsFromPage(document, currentUrl);
//                postList.addAll(crawledPost);
//
//                // Check for next page
//                Elements nav = document.select("li[class=prevnext]");
//                currentUrl = "";
//                for (Element e : nav) {
//                    if (e.text().contains("Next")) {
//                        String uri = e.select("a[href]").attr("href");
//                        currentUrl = String.format("https://forums.hardwarezone.com.sg%s", uri);
//                        break;
//                    }
//                }
//                if(!currentUrl.isEmpty()) {
//                    currentPage+=1;
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//                break;
//            }
//        }
//
//        // based on last url update page last processed
//        threadRepository.save(new Thread(url, currentPage));
//        postRepository.saveAll(postList);
//    }
//
//    private List<Post> crawlPostsFromPage(Document document, String url) {
//        Pattern postCountPattern = Pattern.compile("^\\/(\\d+)-post(\\d+)\\.html");
//        List<Post> postList = new ArrayList<>();
//
//        String title = document.select("div#forum[class=inner] > h2[class=header-gray]").text();
//        Elements postWrappers = document.getElementsByClass("post-wrapper");
//        for (Element p : postWrappers) {
//
//            // Extract Post Number, Thread Number
//            long globalPostNumber = 0L;
//            long threadPostNumber = 0L;
//            String postString = p.select("td.thead[align=right] > a").attr("href").trim();
//            Matcher m = postCountPattern.matcher(postString);
//            if (m.find()) {
//                globalPostNumber = this.convertNumberStringToLong(m.group(1).trim());
//                threadPostNumber = this.convertNumberStringToLong(m.group(2).trim());
//            }
//
//            // Extract Post Datetime
//            Date postDateTime = null;
//            try {
//                postDateTime = this.convertPostDate(p.select("td.thead").not("[align]").text().trim());
//            } catch (ParseException e) {
//                e.printStackTrace();
//            }
//
//            // Extract Profile Details (userName, avatarUrl, status, postCount)
//            String userName = p.select("a[class=bigusername]").text();
//            String avatarUrl = p.select("div[class=smallfont] > a > img").attr("src");
//            String[] profile = p.select("td[class=alt2] > div[class=smallfont]")
//                    .text().split("Join Date: |Posts: ");
//            String status = profile[0];
//            long postCount = this.convertNumberStringToLong(profile[2].trim()); // 5,859
//
//            // Extract Profile Details (joinedDate)
//            Date joinedDate = null; // Oct 2017
//            try {
//                joinedDate = this.convertJoinedDate(profile[1]);
//            } catch (ParseException e) {
//                e.printStackTrace();
//            }
//
//            // Extract Quotes
//            List<Quote> quoteList = new ArrayList<>();
//            Elements quoteElements = p.select("td[class=alt1] > div.post_message > div.quote");
//            for (Element q : quoteElements) {
//                String quoteUser = q.select("span > strong").text();
//                String quoteText = q.select("blockquote").text();
//                quoteList.add(new Quote(quoteUser, quoteText));
//            }
//
//            // Extract Images
//            List<String> imageList = new ArrayList<>();
//            Elements imageElements = p.select("td[class=alt1] > div.post_message > a > img");
//            for (Element i : imageElements) {
//                imageList.add(i.attr("src"));
//            }
//
//            // Extract Text
//            String text = p.select("td[class=alt1] > div.post_message").select("div").remove().first().text();
//
//            long id = globalPostNumber;
//            Post post = new Post(id, title, url, threadPostNumber, postDateTime,
//                    userName, avatarUrl, status, joinedDate, postCount, text, imageList, quoteList);
//            postList.add(post);
//        }
//
//        return postList;
//    }
//
//    // Loop through HWZ by pages and identifying thread within page
//    public void identifyThreadByPage(int pages) {
//        final String url = "https://forums.hardwarezone.com.sg/eat-drink-man-woman-16/index%d.html";
//
//        for (int i = 1; i <= pages; i++) {
//            Document document = null;
//            try {
//                String currentUrl = String.format(url, i);
//                document = Jsoup.connect(currentUrl).get();
//                log.info("Processing page #{} at {}", i, currentUrl);
//
//                //Elements threadWrappers = document.select("tbody#threadbits_forum_16 > tr").not("[class=hwz-sticky]").not("[class=hwz-promoted]");
//                Elements threadsObject = document.select("div.structItem-title");
//                log.info("Found {} thread(s) in page #{}", threadsObject.size(), i);
//
//                for (Element t : threadsObject) {
//                    String threadUrl = String.format("https://forums.hardwarezone.com.sg%s", t.select("td[class=alt1] > div > a").attr("href"));
//                    log.info("Tasking thread for processing {}, {}", t.text(), threadUrl);
//                    this.crawlThread(threadUrl);
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//                break;
//            }
//        }
//    }

}
