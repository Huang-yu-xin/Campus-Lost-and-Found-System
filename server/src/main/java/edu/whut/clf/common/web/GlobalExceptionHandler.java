package edu.whut.clf.common.web;

import edu.whut.clf.common.error.BusinessException;
import edu.whut.clf.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

/**
 * 统一异常处理：转为 { code, message, data:null, requestId }，不泄漏堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest req) {
        ErrorCode code = ex.getErrorCode();
        String requestId = requestId(req);
        // 业务异常记为 warn，不打印堆栈到生产错误级别
        log.warn("business error [{}] {} at {}", code.name(), ex.getMessage(), req.getRequestURI());
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), ex.getMessage(), requestId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        FieldError fe = ex.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getField() + ": " + fe.getDefaultMessage() : "参数校验失败";
        return ResponseEntity.status(ErrorCode.INVALID_ARGUMENT.httpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_ARGUMENT.name(), msg, requestId(req)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        ErrorCode code = ErrorCode.FILE_TOO_LARGE;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), code.defaultMessage(), requestId(req)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        ErrorCode code = ErrorCode.NOT_FOUND;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), code.defaultMessage(), requestId(req)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        // 不泄漏解析细节
        ErrorCode code = ErrorCode.INVALID_ARGUMENT;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), "请求体格式错误", requestId(req)));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.multipart.MultipartException.class,
            java.time.format.DateTimeParseException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_ARGUMENT", "请求参数格式错误", requestId(req)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest req) {
        String requestId = requestId(req);
        // 未预期异常记录完整堆栈到服务端日志（不返回给客户端）
        log.error("unexpected error requestId={} uri={}", requestId, req.getRequestURI(), ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code.name(), code.defaultMessage(), requestId));
    }

    private String requestId(HttpServletRequest req) {
        Object rid = req.getAttribute("requestId");
        return rid != null ? rid.toString() : UUID.randomUUID().toString();
    }
}
