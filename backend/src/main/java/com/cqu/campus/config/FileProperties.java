package com.cqu.campus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置项
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.file")
public class FileProperties {

    /** 文件根目录 */
    private String root = "./uploads";

    /** 单文件上限（MB） */
    private int maxSizeMb = 20;
}
