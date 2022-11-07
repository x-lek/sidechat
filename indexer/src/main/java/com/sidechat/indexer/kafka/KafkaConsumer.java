package com.sidechat.indexer.kafka;

import com.sidechat.entity.Post;
import com.sidechat.entity.Image;
import com.sidechat.indexer.repository.PostRepository;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
@AllArgsConstructor
public class KafkaConsumer {

    @Autowired
    private final PostRepository postRepository;

    @Autowired
    private final MinioClient minioClient;

    @KafkaListener(topics = "post", groupId = "indexer")
    public void consume(@Payload Post post) {
        log.info(post.toString());

        String threadId = String.valueOf(post.getThreadId());

        // download and upload images into minio
        post.getImages().forEach(image -> {
            HttpURLConnection headMediaUrlConnection = null;
            try {
                headMediaUrlConnection = (HttpURLConnection) (new URL(image.getUrl())).openConnection();
                headMediaUrlConnection.setRequestMethod("HEAD");
                headMediaUrlConnection.connect();

                int contentLength = headMediaUrlConnection.getContentLength();
                headMediaUrlConnection.disconnect();

                try (InputStream in = (new URL(image.getUrl())).openStream()) {
                    if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(threadId).build())) {
                        minioClient.makeBucket(MakeBucketArgs.builder().bucket(threadId).build());
                    }
                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(threadId)
                            .object(image.getTitle())
                            .stream(in, contentLength, -1)
                            .build());
                } catch (ServerException e) {
                    throw new RuntimeException(e);
                } catch (InsufficientDataException e) {
                    throw new RuntimeException(e);
                } catch (ErrorResponseException e) {
                    throw new RuntimeException(e);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                } catch (InvalidKeyException e) {
                    throw new RuntimeException(e);
                } catch (InvalidResponseException e) {
                    throw new RuntimeException(e);
                } catch (XmlParserException e) {
                    throw new RuntimeException(e);
                } catch (InternalException e) {
                    throw new RuntimeException(e);
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // index document into elastic
        postRepository.save(post);
    }
}
