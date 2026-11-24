package com.cqu.campus.controller;

import com.cqu.campus.common.ApiResponse;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.service.ArchiveService;
import com.cqu.campus.domain.vo.ArchiveVO;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 归档接口
 */
@Validated
@RestController
public class ArchiveController {

    private final ArchiveService archiveService;

    public ArchiveController(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @PostMapping(value = "/api/activities/{id}/archives", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ArchiveVO> upload(@PathVariable("id") Long id,
                                         @RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "summary", required = false) String summary,
                                         @RequestParam(value = "visibility", required = false) String visibility) {
        return ApiResponse.success(archiveService.upload(id, file, summary, visibility));
    }

    @GetMapping("/api/activities/{id}/archives")
    public ApiResponse<PageResult<ArchiveVO>> list(@PathVariable("id") Long id,
                                                   @RequestParam(value = "pageNum", required = false) Integer pageNum,
                                                   @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.success(archiveService.list(id, pageNum, pageSize));
    }

    @DeleteMapping("/api/activities/{id}/archives/{archiveId}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id,
                                    @PathVariable("archiveId") Long archiveId) {
        archiveService.delete(id, archiveId);
        return ApiResponse.success();
    }
}
