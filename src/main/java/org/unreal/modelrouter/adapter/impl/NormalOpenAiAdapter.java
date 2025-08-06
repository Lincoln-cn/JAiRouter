package org.unreal.modelrouter.adapter.impl;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.adapter.BaseAdapter;
import org.unreal.modelrouter.config.ModelServiceRegistry;
import org.unreal.modelrouter.dto.SttDTO;

public class NormalOpenAiAdapter extends BaseAdapter {

    public NormalOpenAiAdapter(ModelServiceRegistry registry) {
        super(registry);
    }

    @Override
    protected String getAdapterType() {
        return "normal";
    }

    @Override
    protected Object transformRequest(Object request, String adapterType) {
        if (request instanceof SttDTO.Request sttRequest) {
            return transformSttRequest(sttRequest);
        }
        return super.transformRequest(request, adapterType);
    }

    /**
     * STT请求需要特殊的multipart处理
     */
    private Object transformSttRequest(SttDTO.Request sttRequest) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("model", sttRequest.model());
        builder.part("language", sttRequest.language());

        // 使用 asyncPart 处理文件内容流
        builder.asyncPart("file", sttRequest.file().content(), DataBuffer.class)
                .filename(sttRequest.file().filename())
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        return builder.build();
    }

    @Override
    protected <T> WebClient.RequestBodySpec configureRequestHeaders(WebClient.RequestBodySpec requestSpec, T request) {
        // Normal adapter保持标准的OpenAI格式，不需要特殊的头部配置
        return super.configureRequestHeaders(requestSpec, request);
    }
}