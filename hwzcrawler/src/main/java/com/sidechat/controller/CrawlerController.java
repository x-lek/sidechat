package com.sidechat.controller;

import com.sidechat.entity.Post;
import com.sidechat.entity.Thread;
import com.sidechat.service.CrawlerService;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class CrawlerController {
    @Autowired
    private final CrawlerService crawlerService;

    //@Autowired
    //private final ThreadRepository threadRepository;

    @GetMapping("/api/thread")
    public void crawlThread(@RequestParam String threadId) {
        Optional<Thread> threadOptional = crawlerService.getThreadInfo(threadId);

        if(threadOptional.isPresent()) {
            log.info("Crawling post from thread {}", threadId);
            List<Post> postList = crawlerService.processThread(threadOptional.get());
            log.info("Crawled {} posts from thread {}", postList.size(), threadOptional.get().getTitle());

        } else {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "thread not found"
            );
        }
    }

    @GetMapping("/api/pages")
    public void crawlPage(@RequestParam int p) {
        crawlerService.processTopXForumPages(p);
    }
}
