package com.cqu.campus.common;

/**
 * 统一业务错误码
 */
public enum ErrorCode {

    COMMON_INVALID_PARAM(400, "参数校验失败"),
    AUTH_REQUIRED(401, "未登录或令牌无效"),
    AUTH_FORBIDDEN(403, "角色或资源归属不足"),
    USER_EXISTS(400, "账号或学号已存在"),
    CLUB_EXISTS(400, "社团名称已被占用"),
    CLUB_FROZEN(409, "社团已冻结"),
    USER_DISABLED(403, "账号已冻结或停用"),
    RESOURCE_NOT_FOUND(404, "对象不存在"),
    ACTIVITY_CLOSED(409, "活动已关闭或不在报名窗口"),
    ACTIVITY_OFFLINE(409, "活动已被平台下架，需平台管理员恢复"),
    SIGNUP_DUPLICATE(409, "重复报名"),
    SIGNUP_FULL(409, "名额已满"),
    STATE_CONFLICT(409, "状态已被他人变更"),
    TIME_INVALID(400, "时间设置非法"),
    CAPACITY_INVALID(400, "人数上限非法"),
    QUERY_RANGE_INVALID(400, "统计时间范围非法"),
    SIGNIN_WINDOW_CLOSED(409, "不在签到窗口内"),
    ARCHIVE_LIMIT(409, "单活动归档数量已达上限"),
    FILE_INVALID(400, "文件校验失败"),
    FILE_STORE_FAILED(500, "文件存储失败"),
    SYSTEM_ERROR(500, "系统异常");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
