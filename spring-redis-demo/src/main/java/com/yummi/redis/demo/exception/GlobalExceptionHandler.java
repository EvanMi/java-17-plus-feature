package com.yummi.redis.demo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局统一异常拦截器。
 * 捕获应用中抛出的异常，统一封装为结构化的 JSON 响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

//    /**
//     * 处理参数缺失、类型不匹配等客户端错误。
//     */
//    @ExceptionHandler({
//            IllegalArgumentException.class,
//            IllegalStateException.class,
//            NumberFormatException.class
//    })
//    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
//        return build(HttpStatus.BAD_REQUEST, "请求参数错误", ex);
//    }
//
//    /**
//     * 处理 404 资源不存在。
//     */
//    @ExceptionHandler(NoResourceFoundException.class)
//    public ResponseEntity<Map<String, Object>> handleNotFound(NoResourceFoundException ex) {
//        return build(HttpStatus.NOT_FOUND, "资源不存在", ex);
//    }
//
//    /**
//     * 兜底处理所有未捕获的异常。
//     */
//    @ExceptionHandler(Exception.class)
//    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
//        return build(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误", ex);
//    }
//
//    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, Exception ex) {
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("timestamp", LocalDateTime.now().toString());
//        body.put("status", status.value());
//        body.put("error", status.getReasonPhrase());
//        body.put("message", message);
//        body.put("detail", ex.getMessage());
//        return ResponseEntity.status(status).body(body);
//    }
}