package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站内消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO {

    private Long id;
    private String type;
    private String title;
    private String content;
    private Boolean isRead;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
