package edu.whut.clf.common.error;

/**
 * 业务异常，携带稳定 ErrorCode。由 GlobalExceptionHandler 统一转为响应。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public static BusinessException of(ErrorCode code) {
        return new BusinessException(code);
    }
}
