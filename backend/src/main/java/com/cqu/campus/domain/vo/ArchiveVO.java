package com.cqu.campus.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 归档资料
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveVO {

    private Long id;
    private Long activityId;
    private Long fileId;
    private String originalName;
    private String mimeType;
    private Long sizeBytes;
    private String summary;
    private String visibility;
    private Integer version;
    private Long uploaderId;
    private String uploaderName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
