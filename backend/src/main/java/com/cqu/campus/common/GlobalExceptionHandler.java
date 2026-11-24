package com.cqu.campus.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 全局异常处理：对外只暴露业务码、可读提示与 traceId，不返回堆栈
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return build(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return build(ErrorCode.COMMON_INVALID_PARAM, fieldMessages(e.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException e) {
        return build(ErrorCode.COMMON_INVALID_PARAM, fieldMessages(e.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        List<String> messages = new ArrayList<String>();
        for (ConstraintViolation<?> item : violations) {
            messages.add(item.getPropertyPath() + ": " + item.getMessage());
        }
        return build(ErrorCode.COMMON_INVALID_PARAM, messages.isEmpty() ? "参数校验失败" : join(messages));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return build(ErrorCode.COMMON_INVALID_PARAM, "缺少参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return build(ErrorCode.COMMON_INVALID_PARAM, "参数类型不正确: " + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return build(ErrorCode.COMMON_INVALID_PARAM, "请求体格式不正确");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException e) {
        return build(ErrorCode.FILE_INVALID, "上传文件超过大小限制");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return new ResponseEntity<ApiResponse<Void>>(
                ApiResponse.<Void>error(ErrorCode.COMMON_INVALID_PARAM, "不支持的请求方法: " + e.getMethod()),
                HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("未处理异常 traceId={}", org.slf4j.MDC.get(TraceIdFilter.MDC_KEY), e);
        return build(ErrorCode.SYSTEM_ERROR, "系统异常，请稍后重试");
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode errorCode, String message) {
        return new ResponseEntity<ApiResponse<Void>>(ApiResponse.<Void>error(errorCode, message),
                HttpStatus.valueOf(errorCode.getHttpStatus()));
    }

    private String fieldMessages(List<FieldError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "参数校验失败";
        }
        List<String> messages = new ArrayList<String>();
        for (FieldError error : errors) {
            messages.add(error.getField() + ": " + error.getDefaultMessage());
        }
        return join(messages);
    }

    private String join(List<String> messages) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) {
                builder.append("; ");
            }
            builder.append(messages.get(i));
        }
        return builder.toString();
    }
}
