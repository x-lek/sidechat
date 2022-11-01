package com.xavier.sidechat.kafka;

import com.xavier.sidechat.PostAvro;
import com.xavier.sidechat.entity.Post;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.BeanUtils;

@Service
public class KafkaProducer {

    @Value(value = "${kafka.topic}")
    private String topic;

    @Autowired
    private KafkaTemplate<String, PostAvro> postKafkaProducer;

    public void send(PostAvro postAvro) {
//        PostAvro postAvro = new PostAvro();
//        BeanUtils.copyProperties(post, postAvro);

        ProducerRecord<String, PostAvro> producerRecord = new ProducerRecord<String, PostAvro>(topic, postAvro);
        postKafkaProducer.send(producerRecord);
    }
}
