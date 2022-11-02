package com.xavier.sidechat.service;

import com.xavier.sidechat.entity.Image;
import com.xavier.sidechat.entity.Post;
import com.xavier.sidechat.entity.Quote;
import com.xavier.sidechat.entity.Thread;
import com.xavier.sidechat.kafka.KafkaProducer;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
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

    @Value("${sidechat.crawler.url}")
    private String crawlUrl;

    @Value("${hwzcrawler.thread.url}")
    private String crawlThreadUrl;

    @Value("${sidechat.crawler.max-thread-page}")
    private Long crawlThreadMaxPage;

    @Scheduled(cron = "0 0/60 * * * ?")
    public void doProcessTop20Pages() {
        this.processTopXForumPages(20);
    }

    private final String USER_AGENT = "Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6";

    private final KafkaProducer kafkaProducer;

    public CrawlerService(KafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssXXX");
    //SimpleDateFormat threadDateFormatter = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm aa");

    /* Main Function, identify list of threads
       Parallelized collection of job by threads; 1 process per forumPage */
    public void processTopXForumPages(int pages) {
        // Crawl thread information within top X pages
        List<Thread> threadList = this.getAllThreadsInTopPages(pages);

        // Determine if thread has new posts
        List<Post> postList = StreamSupport.stream(threadList.spliterator(), true)
                .map(this::processThread)
                .flatMap(posts -> posts.stream())
                .collect(Collectors.toList());

        log.info("Collected {} posts", postList.size());
    }

    /* Collect thread information within top pages */
    private List<Thread> getAllThreadsInTopPages(int pages) {
        //https://forums.hardwarezone.com.sg/eat-drink-man-woman-16/index%d.html

        List<Thread> threadList = new ArrayList<>();
        for (int i = 1; i <= pages; i++) {
            Document document = null;
            try {
                String currentPageUrl = String.format(crawlUrl, i);
                document = Jsoup.connect(currentPageUrl)
                        .userAgent(USER_AGENT)
                        .get();
                log.debug("Processing page #{} at {}", i, currentPageUrl);

                Elements threadsObjects = document.select(
                        "div.structItemContainer-group.js-threadList > " +
                                    "div.structItem--thread");

                for (Element t : threadsObjects) {
                    String threadUrl = String.format(crawlThreadUrl,
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
                        log.warn("Unable to get thread last modified date");
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
                            log.warn("Unable to get last page");
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

    /* Extract individual post information */
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
                // foreach image, download from data-src and putObject into MinIO
                URL mediaUrl = new URL(e.attr("data-src"));

//                HttpURLConnection headMediaUrlConnection = (HttpURLConnection) mediaUrl.openConnection();
//                headMediaUrlConnection.setRequestMethod("HEAD");
//                headMediaUrlConnection.connect();
//                int contentLength = headMediaUrlConnection.getContentLength();
//                headMediaUrlConnection.disconnect();
//
//                // check contentLength is not valid, download and saveObject
//                try(InputStream in = mediaUrl.openStream()){
//                    if(!minioClient.bucketExists(BucketExistsArgs.builder().bucket(thread.getId()).build())) {
//                        minioClient.makeBucket(MakeBucketArgs.builder().bucket(thread.getId()).build());
//                    }
//                    minioClient.putObject(PutObjectArgs.builder()
//                            .bucket(thread.getId())
//                            .object(e.attr("title"))
//                            .stream(in, contentLength, -1)
//                            .build());
//                }

                // prepare metadata for images[]
                imageList.add(new Image(
                        e.attr("data-src"),
                        e.attr("title")
                ));
            }

            List<Quote> quoteList = new ArrayList<>();
            for (Element e : bodyElement.select("blockquote")) {
                try {
                    Element quoteHeaderElement = e.select("a.bbCodeBlock-sourceJump").first();
                    if(quoteHeaderElement == null) {
                        continue;
                    }
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
                    log.warn("Unable to get quote element");
                    ignored.printStackTrace();
                }
            }

            Post post = Post.newBuilder()
                    .setId(id)
                    .setThreadId(thread.getId())
                    .setThreadTitle(thread.getTitle())
                    .setLocalPostId(localPostId)
                    .setAuthor(author)
                    .setAuthorPostCount(authorPostCount)
                    .setPublishedDate(publishedDate.toInstant())
                    .setPublishedDateString(publishedDateString)
                    .setText(text)
                    .setHtml(html)
                    .setImages(imageList.stream().map(
                            image ->
                                Image.newBuilder()
                                        .setTitle(image.getTitle())
                                        .setUrl(image.getUrl())
                                        .build()
                    ).toList())
                    .setQuotes(quoteList.stream().map(
                            quote -> Quote.newBuilder()
                                    .setPostId(quote.getPostId())
                                    .setAuthor(quote.getAuthor())
                                    .setText(quote.getText())
                                    .build()
                    ).toList())
                    .build();

            /* Send collected information to kafka */
            kafkaProducer.send(post);

            return Optional.of(post);
        } catch (Exception e) {
            log.error("Unable to create post: {}", e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
    }

    /* Parallelized collection of post within page; 1 process per post collection */
    private List<Post> processThreadPage(Thread thread, Long page) {
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
        return postListByPage;
    }

    /* Parallelized collection of thread by pages; 1 process per page
    *  Main Function for post collection of specific thread */
    public List<Post> processThread(Thread thread) {
        if(thread.getLastPage() > crawlThreadMaxPage) {
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
                    yield Long.parseLong(numString.replaceAll("[^0-9]", ""));
                } catch (Exception pe) {
                    pe.printStackTrace();
                    yield  0L;
                }
            }
        };
    }
}
