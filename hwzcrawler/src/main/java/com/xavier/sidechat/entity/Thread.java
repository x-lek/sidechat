package com.xavier.sidechat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.util.Date;

@Builder
@Data
@AllArgsConstructor
public class Thread {
    @Id
    String id;
    String url;
    String title;
    String creator;
    Date startDate;
    Long viewCount;
    Long lastPage;
    Date lastModified;
}
