package com.cqu.campus.controller;

import com.cqu.campus.domain.dto.FileDownload;
import com.cqu.campus.service.ArchiveService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件下载接口：经鉴权后返回受控流
 */
@RestController
public class FileController {

    private final ArchiveService archiveService;

    public FileController(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @GetMapping("/api/files/{id}")
    public ResponseEntity<Resource> download(@PathVariable("id") Long id) {
        FileDownload download = archiveService.download(id);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (download.getMimeType() != null) {
            try {
                mediaType = MediaType.parseMediaType(download.getMimeType());
            } catch (IllegalArgumentException ignored) {
                // 未知 MIME 类型按二进制流返回
            }
        }
        String fileName = download.getFileName() == null ? "archive" : download.getFileName();
        String fallback = fileName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "");
        String encoded;
        try {
            encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            encoded = fallback;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded)
                .body(download.getResource());
    }
}
