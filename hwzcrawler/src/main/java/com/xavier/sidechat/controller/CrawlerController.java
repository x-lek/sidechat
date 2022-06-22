package com.xavier.sidechat.controller;

import com.xavier.sidechat.entity.Post;
import com.xavier.sidechat.entity.Thread;
import com.xavier.sidechat.repository.ThreadRepository;
import com.xavier.sidechat.service.CrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
public class CrawlerController {
    @Autowired
    private final CrawlerService crawlerService;

    @Autowired
    private final ThreadRepository threadRepository;

    public CrawlerController(CrawlerService crawlerService, ThreadRepository threadRepository) {
        this.crawlerService = crawlerService;
        this.threadRepository = threadRepository;
    }

    @GetMapping("/api/threads")
    public void crawlThread(@RequestParam String threadId) {
        Optional<Thread> threadOptional = threadRepository.findById(threadId);

        if(threadOptional.isPresent()) {
            log.info("Crawling post from thread {}", threadId);
            List<Post> postList = crawlerService.getPostFromThread(threadOptional.get());
            log.info("Crawled {} posts from thread {}", postList.size(), threadOptional.get().getTitle());

//            for(Post p : postList) {
//                log.info(p.toString());
//            }
        } else {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "thread not found"
            );
        }
    }

    @GetMapping("/api/pages")
    public void crawlPage(@RequestParam int p) {
        crawlerService.processTopPages(p);
    }
}
