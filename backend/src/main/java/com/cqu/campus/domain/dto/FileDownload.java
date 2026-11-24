package com.cqu.campus.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.io.Resource;

/**
 * 受控文件下载载体：仅暴露资源流与展示信息，不暴露物理路径
 */
@Data
@AllArgsConstructor
public class FileDownload {

    private Resource resource;
    private String fileName;
    private String mimeType;
}
