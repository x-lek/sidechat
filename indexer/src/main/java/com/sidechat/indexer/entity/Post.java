package com.sidechat.indexer.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Date;
import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(indexName = "post")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Post {

    @Id
    Long id;
    String threadId;
    String threadTitle;
    int localPostId;

    @JsonFormat(pattern="yyyyMMdd'T'HHmmss.SSS")
    Date publishedDate;
    String publishedDateString;
    String author;
    Long authorPostCount;
    String html;
    String text;
    List<Image> images;
    List<Quote> quotes;
}
