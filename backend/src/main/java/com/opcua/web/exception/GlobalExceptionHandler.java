package com.opcua.web.exception;

import com.opcua.web.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
            .body(new ApiError(
                ex.getStatus().value(),
                ex.getCode(),
                ex.getMessage(),
                ex.getDetails(),
                request.getRequestURI(),
                Instant.now().toString()
            ));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiError> handleValidation(Exception ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (ex instanceof MethodArgumentNotValidException manve) {
            details.put("errors", manve.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                .collect(Collectors.toList()));
        } else if (ex instanceof BindException be) {
            details.put("errors", be.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                .collect(Collectors.toList()));
        }
        return ResponseEntity.badRequest()
            .body(new ApiError(
                400,
                "VALIDATION_ERROR",
                "参数校验失败",
                details,
                request.getRequestURI(),
                Instant.now().toString()
            ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
            .body(new ApiError(
                400,
                "BAD_JSON",
                "请求内容格式错误，请检查 JSON 和枚举取值",
                null,
                request.getRequestURI(),
                Instant.now().toString()
            ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new ApiError(
                413,
                "FILE_TOO_LARGE",
                "上传文件超过 20MB 限制",
                null,
                request.getRequestURI(),
                Instant.now().toString()
            ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
            .body(new ApiError(
                400,
                "INVALID_ARGUMENT",
                ex.getMessage() == null ? "参数错误" : ex.getMessage(),
                null,
                request.getRequestURI(),
                Instant.now().toString()
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled request error on {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError(
                500,
                "INTERNAL_ERROR",
                "服务器内部错误: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()),
                null,
                request.getRequestURI(),
                Instant.now().toString()
            ));
    }
}
