package org.unreal.modelrouter.router.adapter.support;

import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.adapter.builder.RequestBuilder;
import org.unreal.modelrouter.router.adapter.handler.ResponseHandler;
import org.unreal.modelrouter.router.adapter.mapper.ResponseMapper;
import org.unreal.modelrouter.router.adapter.processor.HttpRequestProcessor;
import org.unreal.modelrouter.router.adapter.request.NonStreamingRequestProcessor;
import org.unreal.modelrouter.router.adapter.selector.InstanceSelector;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;

/**
 * RequestProcessingSupport - 请求处理支持组件
 * 聚合请求处理相关的依赖，减少 BaseAdapter 构造函数参数。
 *
 * @since v2.28.0
 */
@Component
public class RequestProcessingSupport {

    private final RequestBuilder requestBuilder;
    private final ResponseHandler responseHandler;
    private final InstanceSelector instanceSelector;
    private final ResponseTransformer responseTransformer;
    private final HttpRequestProcessor httpRequestProcessor;
    private final ResponseMapper responseMapper;
    private final NonStreamingRequestProcessor nonStreamingProcessor;

    public RequestProcessingSupport(final RequestBuilder requestBuilder,
                                    final ResponseHandler responseHandler,
                                    final InstanceSelector instanceSelector,
                                    final ResponseTransformer responseTransformer,
                                    final HttpRequestProcessor httpRequestProcessor,
                                    final ResponseMapper responseMapper,
                                    final NonStreamingRequestProcessor nonStreamingProcessor) {
        this.requestBuilder = requestBuilder;
        this.responseHandler = responseHandler;
        this.instanceSelector = instanceSelector;
        this.responseTransformer = responseTransformer;
        this.httpRequestProcessor = httpRequestProcessor;
        this.responseMapper = responseMapper;
        this.nonStreamingProcessor = nonStreamingProcessor;
    }

    public RequestBuilder getRequestBuilder() {
        return requestBuilder;
    }

    public ResponseHandler getResponseHandler() {
        return responseHandler;
    }

    public InstanceSelector getInstanceSelector() {
        return instanceSelector;
    }

    public ResponseTransformer getResponseTransformer() {
        return responseTransformer;
    }

    public HttpRequestProcessor getHttpRequestProcessor() {
        return httpRequestProcessor;
    }

    public ResponseMapper getResponseMapper() {
        return responseMapper;
    }

    public NonStreamingRequestProcessor getNonStreamingProcessor() {
        return nonStreamingProcessor;
    }
}
