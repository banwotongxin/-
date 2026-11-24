package com.cqu.campus.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 导出文件载体（CSV 内容与建议文件名）
 */
@Data
@AllArgsConstructor
public class ExportFile {

    private String fileName;
    private String content;
}
