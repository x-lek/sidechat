package com.xavier.sidechat.repository;

import com.xavier.sidechat.entity.Post;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;

@Component
public interface PostRepository extends ElasticsearchRepository<Post, Long> {
}
