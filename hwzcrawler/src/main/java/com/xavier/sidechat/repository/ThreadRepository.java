package com.xavier.sidechat.repository;

import com.xavier.sidechat.entity.Thread;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Component;

@Component
public interface ThreadRepository extends ElasticsearchRepository<Thread, String> {
}
