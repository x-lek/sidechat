package com.xavier.sidechat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Builder
@Data
@AllArgsConstructor
public class Post {

    @Id
    Long id;
    String threadId;
    Date publishedDate;
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

