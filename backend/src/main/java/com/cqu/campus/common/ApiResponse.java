package com.cqu.campus.common;

import lombok.Data;
import org.slf4j.MDC;

/**
 * 统一响应体
 * 成功：code=0；失败：code 为错误码字符串
 */
@Data
public class ApiResponse<T> {

    private Object code;
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResponse<T> success() {
        return build(0, "success", null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return build(0, "success", data);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return build(errorCode.name(), message == null ? errorCode.getDefaultMessage() : message, null);
    }

    private static <T> ApiResponse<T> build(Object code, String message, T data) {
        ApiResponse<T> response = new ApiResponse<T>();
        response.setCode(code);
        response.setMessage(message);
        response.setData(data);
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        response.setTraceId(traceId == null ? "" : traceId);
        return response;
    }
}
