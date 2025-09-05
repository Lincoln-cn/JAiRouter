package org.unreal.modelrouter.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/v1/debug")
public class DebugMultipartController {

    /**
     * 返回请求元信息（header/content-type/boundary等，全部用字符串，避免CodecException）
     */
    @PostMapping("/multipart-info")
    public Mono<ResponseEntity<Map<String, Object>>> debugMultipartInfo(ServerWebExchange exchange) {
        log.info("收到multipart调试请求");

        Map<String, Object> debugInfo = new HashMap<>();

        // 收集请求头信息
        HttpHeaders headers = exchange.getRequest().getHeaders();
        debugInfo.put("contentType", headers.getContentType() != null ? headers.getContentType().toString() : null);
        debugInfo.put("contentLength", headers.getContentLength());
        debugInfo.put("allHeaders", headers.toSingleValueMap());

        // 认证头信息
        debugInfo.put("xApiKey", headers.getFirst("X-API-Key"));
        debugInfo.put("jairouterToken", headers.getFirst("Jairouter_token"));
        debugInfo.put("authorization", headers.getFirst("Authorization"));

        // 请求信息
        debugInfo.put("method", exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : null);
        debugInfo.put("path", exchange.getRequest().getPath().value());
        debugInfo.put("uri", exchange.getRequest().getURI().toString());

        // 检查Content-Type是否包含boundary
        String contentType = headers.getFirst("Content-Type");
        if (contentType != null) {
            debugInfo.put("hasBoundary", contentType.contains("boundary"));
            if (contentType.contains("boundary")) {
                String[] parts = contentType.split("boundary=");
                if (parts.length > 1) {
                    String boundary = parts[1].split(";")[0].trim();
                    debugInfo.put("boundary", boundary);
                }
            }
        }

        log.info("调试信息: {}", debugInfo);

        return Mono.just(ResponseEntity.ok(debugInfo));
    }

    /**
     * 直接读取并解析multipart请求的所有part
     */
    @PostMapping("/raw-multipart")
    public Mono<ResponseEntity<Map<String, Object>>> handleRawMultipart(ServerWebExchange exchange) {
        log.info("收到原始multipart请求");

        String contentType = exchange.getRequest().getHeaders().getFirst("Content-Type");
        log.info("Content-Type: {}", contentType);

        if (contentType == null || !contentType.startsWith("multipart/")) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", "请求不是multipart类型");
            errorResult.put("contentType", contentType);
            return Mono.just(ResponseEntity.badRequest().body(errorResult));
        }

        return exchange.getMultipartData()
                .map(multipartData -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "success");
                    result.put("partCount", multipartData.size());
                    result.put("partNames", multipartData.keySet());

                    Map<String, Object> partInfo = new LinkedHashMap<>();
                    multipartData.forEach((name, parts) -> {
                        List<Map<String, Object>> partList = new ArrayList<>();
                        for (Part part : parts) {
                            Map<String, Object> p = new HashMap<>();
                            p.put("type", part.getClass().getSimpleName());
                            p.put("headers", part.headers().toSingleValueMap());
                            if (part instanceof FilePart) {
                                p.put("filename", ((FilePart) part).filename());
                            } else if (part instanceof FormFieldPart) {
                                p.put("value", ((FormFieldPart) part).value());
                            }
                            partList.add(p);
                        }
                        partInfo.put(name, partList);
                    });
                    result.put("partsDetail", partInfo);

                    return ResponseEntity.ok(result);
                })
                .onErrorResume(throwable -> {
                    log.error("处理原始multipart数据时发生错误", throwable);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("status", "error");
                    errorResult.put("message", throwable.getMessage());
                    errorResult.put("errorType", throwable.getClass().getSimpleName());
                    errorResult.put("contentType", contentType);
                    return Mono.just(ResponseEntity.badRequest().body(errorResult));
                });
    }

    /**
     * 演示标准@ReqeustPart用法
     */
    @PostMapping(value = "/simple-multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> handleSimpleMultipart(
            @RequestPart(value = "text", required = false) String text,
            @RequestPart(value = "file", required = false) FilePart filePart,
            ServerWebExchange exchange) {

        log.info("收到简单multipart请求");

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "简单multipart请求处理成功");

        if (text != null) {
            result.put("text", text);
            log.info("接收到文本部分: {}", text);
        }

        if (filePart != null) {
            result.put("fileName", filePart.filename());
            result.put("fileHeaders", filePart.headers().toSingleValueMap());
            log.info("接收到文件部分: {}, 头信息: {}", filePart.filename(), filePart.headers().toSingleValueMap());
        }

        return Mono.just(ResponseEntity.ok(result));
    }

    /**
     * 读取原始body前500字节，便于分析请求体格式
     */
    @PostMapping("/raw-body")
    public Mono<ResponseEntity<Map<String, Object>>> handleRawBody(ServerWebExchange exchange) {
        log.info("收到原始请求体调试请求");

        Map<String, Object> debugInfo = new HashMap<>();
        HttpHeaders headers = exchange.getRequest().getHeaders();

        debugInfo.put("contentType", headers.getContentType() != null ? headers.getContentType().toString() : null);
        debugInfo.put("contentLength", headers.getContentLength());

        // 读取原始请求体的前几个字节来检查格式
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(dataBuffer -> {
                    try {
                        int readableBytes = Math.min(dataBuffer.readableByteCount(), 500);
                        byte[] bytes = new byte[readableBytes];
                        dataBuffer.read(bytes);

                        String bodyPreview = new String(bytes, StandardCharsets.UTF_8);
                        debugInfo.put("bodyPreview", bodyPreview);
                        debugInfo.put("bodyLength", dataBuffer.readableByteCount());

                        debugInfo.put("containsBoundary", bodyPreview.contains("--"));
                        DataBufferUtils.release(dataBuffer);

                        debugInfo.put("status", "success");
                        return ResponseEntity.ok(debugInfo);

                    } catch (Exception e) {
                        log.error("读取请求体时发生错误", e);
                        debugInfo.put("status", "error");
                        debugInfo.put("message", e.getMessage());
                        return ResponseEntity.badRequest().body(debugInfo);
                    }
                })
                .defaultIfEmpty(ResponseEntity.ok(Map.of("status", "success", "message", "空请求体")));
    }

    /**
     * 更灵活地收集所有part类型
     */
    @PostMapping("/flexible-multipart")
    public Mono<ResponseEntity<Map<String, Object>>> handleFlexibleMultipart(
            @RequestBody(required = false) Flux<Part> parts) {

        log.info("收到灵活multipart请求");

        return parts
                .collectList()
                .map(partList -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "success");
                    result.put("partCount", partList.size());

                    List<Map<String, String>> partSummaries = new ArrayList<>();
                    for (Part part : partList) {
                        Map<String, String> summary = new HashMap<>();
                        summary.put("name", part.name());
                        summary.put("type", part.getClass().getSimpleName());
                        if (part instanceof FormFieldPart) {
                            summary.put("value", ((FormFieldPart) part).value());
                        } else if (part instanceof FilePart) {
                            summary.put("filename", ((FilePart) part).filename());
                        }
                        partSummaries.add(summary);
                        log.info("part: {} 类型: {}", part.name(), part.getClass().getSimpleName());
                    }
                    result.put("parts", partSummaries);

                    return ResponseEntity.ok(result);
                })
                .onErrorResume(throwable -> {
                    log.error("处理灵活multipart数据时发生错误", throwable);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("status", "error");
                    errorResult.put("message", throwable.getMessage());
                    errorResult.put("errorType", throwable.getClass().getSimpleName());
                    return Mono.just(ResponseEntity.badRequest().body(errorResult));
                });
    }
}