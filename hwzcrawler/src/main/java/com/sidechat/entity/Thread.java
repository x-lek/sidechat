package com.sidechat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;

@Builder
@Data
@AllArgsConstructor
@Document(indexName = "thread", createIndex = true)
public class Thread {
    @Id
    String id;
    String url;
    String title;
    String creator;

    @Field(type = FieldType.Date, format = DateFormat.basic_date_time, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    Date startDate;
    //Long viewCount;
    Long lastPage;

//    @Field(type = FieldType.Date, format = DateFormat.basic_date_time, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
//    Date lastModified;
}
