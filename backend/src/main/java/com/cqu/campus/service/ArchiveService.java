package com.cqu.campus.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqu.campus.common.BizException;
import com.cqu.campus.common.ErrorCode;
import com.cqu.campus.common.PageResult;
import com.cqu.campus.config.FileProperties;
import com.cqu.campus.domain.dto.FileDownload;
import com.cqu.campus.domain.entity.Activity;
import com.cqu.campus.domain.entity.Archive;
import com.cqu.campus.domain.entity.FileObject;
import com.cqu.campus.domain.entity.UserAccount;
import com.cqu.campus.domain.vo.ArchiveVO;
import com.cqu.campus.mapper.ActivityMapper;
import com.cqu.campus.mapper.ArchiveMapper;
import com.cqu.campus.mapper.FileObjectMapper;
import com.cqu.campus.mapper.UserAccountMapper;
import com.cqu.campus.security.UserContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 归档服务：资料上传、分页展示、版本留痕、受控下载与删除回收
 */
@Service
public class ArchiveService {

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<String>(
            Arrays.asList("jpg", "jpeg", "png", "pdf", "zip", "docx"));
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String VISIBILITY_CLUB_ONLY = "CLUB_ONLY";
    private static final String VISIBILITY_PRIVATE = "PRIVATE";
    private static final int MAX_ARCHIVES_PER_ACTIVITY = 100;
    private static final int MAX_SUMMARY_LENGTH = 1000;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final byte[] DOCX_MARKER = "[Content_Types].xml".getBytes(StandardCharsets.US_ASCII);

    private final ArchiveMapper archiveMapper;
    private final FileObjectMapper fileObjectMapper;
    private final ActivityMapper activityMapper;
    private final UserAccountMapper userAccountMapper;
    private final ActivityService activityService;
    private final ClubService clubService;
    private final FileProperties fileProperties;
    private final AuditService auditService;

    public ArchiveService(ArchiveMapper archiveMapper, FileObjectMapper fileObjectMapper, ActivityMapper activityMapper,
                          UserAccountMapper userAccountMapper, ActivityService activityService, ClubService clubService,
                          FileProperties fileProperties, AuditService auditService) {
        this.archiveMapper = archiveMapper;
        this.fileObjectMapper = fileObjectMapper;
        this.activityMapper = activityMapper;
        this.userAccountMapper = userAccountMapper;
        this.activityService = activityService;
        this.clubService = clubService;
        this.fileProperties = fileProperties;
        this.auditService = auditService;
    }

    /**
     * 上传归档资料：活动须为 CLOSED 或 ARCHIVED（支持补充资料）；总结必填，按活动递增版本
     */
    @Transactional
    public ArchiveVO upload(Long activityId, MultipartFile file, String summary, String visibility) {
        Activity activity = activityService.requireManageActivity(activityId);
        boolean closed = ActivityService.STATUS_CLOSED.equals(activity.getStatus());
        if (!closed && !ActivityService.STATUS_ARCHIVED.equals(activity.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "仅已关闭或已归档的活动可上传资料");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.FILE_INVALID, "上传文件不能为空");
        }
        long maxBytes = (long) fileProperties.getMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BizException(ErrorCode.FILE_INVALID, "文件大小超过 " + fileProperties.getMaxSizeMb() + " MB 限制");
        }
        String originalName = sanitizeFileName(file.getOriginalFilename());
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex < 0) {
            throw new BizException(ErrorCode.FILE_INVALID, "文件缺少扩展名");
        }
        String extension = originalName.substring(dotIndex + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(ErrorCode.FILE_INVALID, "仅支持 jpg/jpeg/png/pdf/zip/docx 文件");
        }
        String safeSummary = summary == null ? "" : summary.trim();
        if (safeSummary.isEmpty()) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "请填写活动总结");
        }
        if (safeSummary.length() > MAX_SUMMARY_LENGTH) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "活动总结不能超过 " + MAX_SUMMARY_LENGTH + " 字");
        }
        String safeVisibility = visibility == null || visibility.trim().isEmpty()
                ? VISIBILITY_PUBLIC : visibility.trim().toUpperCase();
        if (!VISIBILITY_PUBLIC.equals(safeVisibility) && !VISIBILITY_CLUB_ONLY.equals(safeVisibility)
                && !VISIBILITY_PRIVATE.equals(safeVisibility)) {
            throw new BizException(ErrorCode.COMMON_INVALID_PARAM, "可见范围只能是 PUBLIC、CLUB_ONLY 或 PRIVATE");
        }
        Long archivedCount = archiveMapper.selectCount(new QueryWrapper<Archive>().eq("activity_id", activityId));
        if (archivedCount != null && archivedCount >= MAX_ARCHIVES_PER_ACTIVITY) {
            throw new BizException(ErrorCode.ARCHIVE_LIMIT,
                    "单个活动最多归档 " + MAX_ARCHIVES_PER_ACTIVITY + " 个文件，请先删除或更正旧资料");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_STORE_FAILED, "文件读取失败");
        }
        String mimeType = resolveMimeType(extension, bytes);
        Path root = Paths.get(fileProperties.getRoot()).toAbsolutePath().normalize();
        Path tempFile = root.resolve("tmp").resolve(UUID.randomUUID().toString().replace("-", "") + ".tmp");
        String objectKey = LocalDate.now().format(MONTH_FORMATTER) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = root.resolve(objectKey).normalize();
        try {
            Files.createDirectories(tempFile.getParent());
            Files.write(tempFile, bytes);
            Files.createDirectories(target.getParent());
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new BizException(ErrorCode.FILE_STORE_FAILED, "文件写入失败");
        }
        registerRollbackCleanup(target);
        try {
            FileObject fileObject = new FileObject();
            fileObject.setObjectKey(objectKey);
            fileObject.setOriginalName(originalName);
            fileObject.setMimeType(mimeType);
            fileObject.setSizeBytes((long) bytes.length);
            fileObject.setSha256(sha256(bytes));
            fileObject.setCreatedAt(LocalDateTime.now());
            fileObjectMapper.insert(fileObject);

            Archive archive = new Archive();
            archive.setActivityId(activityId);
            archive.setUploaderId(UserContext.get().getUserId());
            archive.setFileId(fileObject.getId());
            archive.setSummary(safeSummary);
            archive.setVisibility(safeVisibility);
            archive.setVersion(nextVersion(activityId));
            archive.setCreatedAt(LocalDateTime.now());
            try {
                archiveMapper.insert(archive);
            } catch (DuplicateKeyException e) {
                throw new BizException(ErrorCode.STATE_CONFLICT, "归档版本冲突，请重试");
            }

            if (closed) {
                UpdateWrapper<Activity> activityWrapper = new UpdateWrapper<Activity>();
                activityWrapper.eq("id", activityId).eq("status", ActivityService.STATUS_CLOSED)
                        .set("status", ActivityService.STATUS_ARCHIVED)
                        .set("updated_at", LocalDateTime.now());
                int rows = activityMapper.update(null, activityWrapper);
                if (rows == 0) {
                    throw new BizException(ErrorCode.STATE_CONFLICT, "活动状态已被他人变更");
                }
            }
            Map<String, Object> after = new HashMap<String, Object>();
            after.put("archiveId", archive.getId());
            after.put("version", archive.getVersion());
            after.put("objectKey", objectKey);
            after.put("sha256", fileObject.getSha256());
            after.put("visibility", safeVisibility);
            auditService.record("UPLOAD_ARCHIVE", "ARCHIVE", archive.getId(), null, after, "SUCCESS");
            return toVo(archive, fileObject, currentUserRealName());
        } catch (RuntimeException e) {
            deleteQuietly(target);
            throw e;
        }
    }

    /**
     * 归档分页列表：草稿活动仅管理者可见，非管理者只能看到 PUBLIC 资料
     */
    public PageResult<ArchiveVO> list(Long activityId, Integer pageNum, Integer pageSize) {
        Activity activity = activityService.requireActivity(activityId);
        boolean manager = activity.getClubId() != null && clubService.canManage(activity.getClubId());
        if (ActivityService.STATUS_DRAFT.equals(activity.getStatus()) && !manager) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权查看该活动归档");
        }
        QueryWrapper<Archive> wrapper = new QueryWrapper<Archive>();
        wrapper.eq("activity_id", activityId);
        if (!manager) {
            boolean clubMember = activity.getClubId() != null && clubService.isMember(activity.getClubId());
            if (clubMember) {
                wrapper.in("visibility", VISIBILITY_PUBLIC, VISIBILITY_CLUB_ONLY);
            } else {
                wrapper.eq("visibility", VISIBILITY_PUBLIC);
            }
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");
        Page<Archive> page = new Page<Archive>(PageResult.safePageNum(pageNum), PageResult.safePageSize(pageSize));
        IPage<Archive> result = archiveMapper.selectPage(page, wrapper);
        List<Archive> archives = result.getRecords();
        List<ArchiveVO> records = new ArrayList<ArchiveVO>();
        if (archives.isEmpty()) {
            return PageResult.of(records, result.getTotal(), pageNum, pageSize);
        }
        List<Long> fileIds = new ArrayList<Long>();
        List<Long> uploaderIds = new ArrayList<Long>();
        for (Archive archive : archives) {
            if (archive.getFileId() != null) {
                fileIds.add(archive.getFileId());
            }
            if (archive.getUploaderId() != null && !uploaderIds.contains(archive.getUploaderId())) {
                uploaderIds.add(archive.getUploaderId());
            }
        }
        Map<Long, FileObject> files = new HashMap<Long, FileObject>();
        if (!fileIds.isEmpty()) {
            for (FileObject fileObject : fileObjectMapper.selectBatchIds(fileIds)) {
                files.put(fileObject.getId(), fileObject);
            }
        }
        Map<Long, String> uploaderNames = new HashMap<Long, String>();
        if (!uploaderIds.isEmpty()) {
            for (UserAccount account : userAccountMapper.selectBatchIds(uploaderIds)) {
                uploaderNames.put(account.getId(), account.getRealName());
            }
        }
        for (Archive archive : archives) {
            records.add(toVo(archive, files.get(archive.getFileId()), uploaderNames.get(archive.getUploaderId())));
        }
        return PageResult.of(records, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 受控下载：按活动可见范围与角色再次校验，并记录下载审计
     */
    public FileDownload download(Long fileId) {
        FileObject fileObject = fileId == null ? null : fileObjectMapper.selectById(fileId);
        if (fileObject == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
        Archive archive = archiveMapper.selectOne(new QueryWrapper<Archive>().eq("file_id", fileId));
        if (archive == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "归档记录不存在");
        }
        Activity activity = activityService.requireActivity(archive.getActivityId());
        boolean manager = activity.getClubId() != null && clubService.canManage(activity.getClubId());
        boolean activityVisible = !ActivityService.STATUS_DRAFT.equals(activity.getStatus());
        boolean publicFile = VISIBILITY_PUBLIC.equals(archive.getVisibility());
        boolean clubMember = activity.getClubId() != null && clubService.isMember(activity.getClubId());
        boolean clubOnlyFile = VISIBILITY_CLUB_ONLY.equals(archive.getVisibility());
        boolean allowed = manager
                || (activityVisible && publicFile)
                || (activityVisible && clubOnlyFile && clubMember);
        if (!allowed) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权下载该归档文件");
        }
        Path target = resolveSafe(fileObject.getObjectKey());
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "文件内容不存在");
        }
        Map<String, Object> after = new HashMap<String, Object>();
        after.put("archiveId", archive.getId());
        after.put("fileId", fileObject.getId());
        after.put("originalName", fileObject.getOriginalName());
        auditService.record("DOWNLOAD_ARCHIVE", "ARCHIVE", archive.getId(), null, after, "SUCCESS");
        Resource resource = new FileSystemResource(target.toFile());
        return new FileDownload(resource, fileObject.getOriginalName(), fileObject.getMimeType());
    }

    /**
     * 删除归档：释放业务引用；仅当无其他归档引用时回收元数据，物理文件在事务提交后删除
     */
    @Transactional
    public void delete(Long activityId, Long archiveId) {
        activityService.requireManageActivity(activityId);
        Archive archive = archiveId == null ? null : archiveMapper.selectById(archiveId);
        if (archive == null || !activityId.equals(archive.getActivityId())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "归档记录不存在");
        }
        FileObject fileObject = archive.getFileId() == null ? null : fileObjectMapper.selectById(archive.getFileId());
        Map<String, Object> before = new HashMap<String, Object>();
        before.put("archiveId", archive.getId());
        before.put("fileId", archive.getFileId());
        before.put("version", archive.getVersion());
        before.put("originalName", fileObject == null ? null : fileObject.getOriginalName());
        archiveMapper.deleteById(archiveId);
        Path target = null;
        if (archive.getFileId() != null) {
            Long refs = archiveMapper.selectCount(new QueryWrapper<Archive>().eq("file_id", archive.getFileId()));
            if (refs == null || refs == 0) {
                fileObjectMapper.deleteById(archive.getFileId());
                if (fileObject != null) {
                    target = resolveSafe(fileObject.getObjectKey());
                }
            }
        }
        auditService.record("DELETE_ARCHIVE", "ARCHIVE", archiveId, before, null, "SUCCESS");
        if (target != null) {
            registerAfterCommitDelete(target);
        }
    }

    private int nextVersion(Long activityId) {
        QueryWrapper<Archive> wrapper = new QueryWrapper<Archive>();
        wrapper.select("MAX(version)").eq("activity_id", activityId);
        List<Object> values = archiveMapper.selectObjs(wrapper);
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return 1;
        }
        return ((Number) values.get(0)).intValue() + 1;
    }

    private String resolveMimeType(String extension, byte[] bytes) {
        String kind = detectFileKind(bytes);
        boolean matched;
        if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            matched = "jpeg".equals(kind);
        } else if ("zip".equals(extension)) {
            matched = "zip".equals(kind) || "docx".equals(kind);
        } else {
            matched = extension.equals(kind);
        }
        if (!matched) {
            throw new BizException(ErrorCode.FILE_INVALID, "文件内容与扩展名不一致");
        }
        if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            return "image/jpeg";
        }
        if ("png".equals(extension)) {
            return "image/png";
        }
        if ("pdf".equals(extension)) {
            return "application/pdf";
        }
        if ("zip".equals(extension)) {
            return "application/zip";
        }
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    private String detectFileKind(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G' && (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A) {
            return "png";
        }
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
                && bytes[3] == 'F' && bytes[4] == '-') {
            return "pdf";
        }
        if (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K'
                && ((bytes[2] & 0xFF) == 3 || (bytes[2] & 0xFF) == 5 || (bytes[2] & 0xFF) == 7)
                && ((bytes[3] & 0xFF) == 4 || (bytes[3] & 0xFF) == 6 || (bytes[3] & 0xFF) == 8)) {
            return contains(bytes, DOCX_MARKER) ? "docx" : "zip";
        }
        return "";
    }

    private boolean contains(byte[] source, byte[] target) {
        if (source.length < target.length) {
            return false;
        }
        for (int i = 0; i <= source.length - target.length; i++) {
            boolean matched = true;
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }
        return false;
    }

    private String sanitizeFileName(String raw) {
        String name = raw == null ? "archive" : raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            name = "archive";
        }
        if (name.length() > 255) {
            String extension = "";
            int dot = name.lastIndexOf('.');
            if (dot > 0 && name.length() - dot <= 10) {
                extension = name.substring(dot);
            }
            name = name.substring(0, 255 - extension.length()) + extension;
        }
        return name;
    }

    private Path resolveSafe(String objectKey) {
        Path root = Paths.get(fileProperties.getRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(objectKey == null ? "" : objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.FILE_INVALID, "文件路径非法");
        }
        return target;
    }

    private String currentUserRealName() {
        Long userId = UserContext.get().getUserId();
        UserAccount account = userAccountMapper.selectById(userId);
        return account == null ? null : account.getRealName();
    }

    private void registerRollbackCleanup(final Path target) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteQuietly(target);
                }
            }
        });
    }

    private void registerAfterCommitDelete(final Path target) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(target);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    deleteQuietly(target);
                }
            }
        });
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理失败不影响对外错误反馈
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder();
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private ArchiveVO toVo(Archive archive, FileObject fileObject, String uploaderName) {
        return ArchiveVO.builder()
                .id(archive.getId())
                .activityId(archive.getActivityId())
                .fileId(archive.getFileId())
                .originalName(fileObject == null ? null : fileObject.getOriginalName())
                .mimeType(fileObject == null ? null : fileObject.getMimeType())
                .sizeBytes(fileObject == null ? null : fileObject.getSizeBytes())
                .summary(archive.getSummary())
                .visibility(archive.getVisibility())
                .version(archive.getVersion())
                .uploaderId(archive.getUploaderId())
                .uploaderName(uploaderName)
                .createdAt(archive.getCreatedAt())
                .build();
    }
}
