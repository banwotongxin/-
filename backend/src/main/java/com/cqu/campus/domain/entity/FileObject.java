package com.cqu.campus.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件对象元数据
 */
@Data
@TableName("file_objects")
public class FileObject {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String objectKey;
    private String originalName;
    private String mimeType;
    private Long sizeBytes;
    private String sha256;
    private LocalDateTime createdAt;
}
