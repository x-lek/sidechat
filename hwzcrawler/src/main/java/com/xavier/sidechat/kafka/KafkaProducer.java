package com.xavier.sidechat.kafka;

import com.xavier.sidechat.entity.Post;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KafkaProducer {

    @Value(value = "${kafka.topic}")
    private String topic;

    @Autowired
    private KafkaTemplate<String, Post> postKafkaProducer;

    public void send(Post post) {
        ProducerRecord<String, Post> producerRecord = new ProducerRecord<String, Post>(topic, post);
        postKafkaProducer.send(producerRecord);
    }
}
