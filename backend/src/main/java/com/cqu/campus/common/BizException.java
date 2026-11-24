package com.cqu.campus.common;

/**
 * 业务异常，携带统一错误码
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
