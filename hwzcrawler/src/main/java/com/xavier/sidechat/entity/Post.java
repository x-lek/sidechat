package com.xavier.sidechat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Builder
@Data
@AllArgsConstructor
@Document(indexName = "post", createIndex = true)
public class Post {

    @Id
    Long id;
    String threadId;
    String threadTitle;
    int localPostId;

    @Field(type = FieldType.Date, format = DateFormat.basic_date_time)
    Date publishedDate;
    String publishedDateString;
    String author;
    Long authorPostCount;
    String html;
    String text;
    List<Image> images;
    List<Quote> quotes;

//    @Override
//    public String toString() {
//        SimpleDateFormat joinFormat = new SimpleDateFormat("MMM yyyy");
//        SimpleDateFormat postFormat = new SimpleDateFormat("dd-MM-YYYY hh:mm aa");
//        return String.format("#%d --------------------------------" +
//                        "\nUsername: %s, Posts: %d, Joined Date: %s \nQuotes: %s\nPosted: %s\n%s\nImages: %s\n",
//                threadPostNumber,
//                userName, userPostCount, joinFormat.format(userJoinedDate),
//                quotes.toString(),
//                postFormat.format(postDatetime),
//                text,
//                images.toString()
//        );
//    }
}

