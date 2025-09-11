package org.unreal.modelrouter.tracing;

/**
 * 追踪常量定义
 * 
 * 定义追踪功能中使用的常量，包括：
 * - 操作名称常量
 * - 属性键常量
 * - 事件名称常量
 * - 标签值常量
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
public final class TracingConstants {
    
    // ========================================
    // 操作名称常量
    // ========================================
    
    /**
     * HTTP操作名称
     */
    public static final class Operations {
        public static final String HTTP_REQUEST = "http.request";
        public static final String HTTP_RESPONSE = "http.response";
        
        // AI服务操作
        public static final String CHAT_REQUEST = "ai.chat";
        public static final String EMBEDDING_REQUEST = "ai.embedding";
        public static final String RERANK_REQUEST = "ai.rerank";
        public static final String TTS_REQUEST = "ai.tts";
        public static final String STT_REQUEST = "ai.stt";
        public static final String IMAGE_GENERATION = "ai.image.generation";
        public static final String IMAGE_EDIT = "ai.image.edit";
        
        // 基础设施操作
        public static final String LOAD_BALANCING = "infrastructure.load_balancing";
        public static final String RATE_LIMITING = "infrastructure.rate_limiting";
        public static final String CIRCUIT_BREAKING = "infrastructure.circuit_breaking";
        public static final String HEALTH_CHECK = "infrastructure.health_check";
        
        // 后端适配器操作
        public static final String BACKEND_CALL = "backend.call";
        public static final String ADAPTER_GPUSTACK = "adapter.gpustack";
        public static final String ADAPTER_OLLAMA = "adapter.ollama";
        public static final String ADAPTER_VLLM = "adapter.vllm";
        public static final String ADAPTER_XINFERENCE = "adapter.xinference";
        public static final String ADAPTER_LOCALAI = "adapter.localai";
        public static final String ADAPTER_OPENAI = "adapter.openai";
        
        // 安全操作
        public static final String AUTHENTICATION = "security.authentication";
        public static final String AUTHORIZATION = "security.authorization";
        public static final String DATA_SANITIZATION = "security.sanitization";
        
        // 配置操作
        public static final String CONFIG_LOAD = "config.load";
        public static final String CONFIG_UPDATE = "config.update";
        public static final String CONFIG_VALIDATION = "config.validation";
        
        private Operations() {}
    }
    
    // ========================================
    // 属性键常量
    // ========================================
    
    /**
     * HTTP属性键
     */
    public static final class HttpAttributes {
        public static final String METHOD = "http.method";
        public static final String URL = "http.url";
        public static final String SCHEME = "http.scheme";
        public static final String HOST = "http.host";
        public static final String TARGET = "http.target";
        public static final String STATUS_CODE = "http.status_code";
        public static final String REQUEST_SIZE = "http.request_content_length";
        public static final String RESPONSE_SIZE = "http.response_content_length";
        public static final String CLIENT_IP = "http.client_ip";
        public static final String USER_AGENT = "http.user_agent";
        public static final String RESPONSE_TIME = "http.response_time_ms";
        
        private HttpAttributes() {}
    }
    
    /**
     * AI服务属性键
     */
    public static final class AiAttributes {
        public static final String SERVICE_TYPE = "ai.service.type";
        public static final String MODEL_NAME = "ai.model.name";
        public static final String MODEL_PROVIDER = "ai.model.provider";
        public static final String REQUEST_TOKENS = "ai.request.tokens";
        public static final String RESPONSE_TOKENS = "ai.response.tokens";
        public static final String TOTAL_TOKENS = "ai.total.tokens";
        public static final String TEMPERATURE = "ai.temperature";
        public static final String MAX_TOKENS = "ai.max_tokens";
        public static final String STREAM = "ai.stream";
        
        private AiAttributes() {}
    }
    
    /**
     * 基础设施属性键
     */
    public static final class InfrastructureAttributes {
        public static final String COMPONENT = "component";
        public static final String STRATEGY = "strategy";
        public static final String INSTANCE = "instance";
        public static final String ALGORITHM = "algorithm";
        public static final String CAPACITY = "capacity";
        public static final String RATE = "rate";
        public static final String TOKENS_REMAINING = "tokens.remaining";
        public static final String CIRCUIT_STATE = "circuit.state";
        public static final String FAILURE_COUNT = "failure.count";
        public static final String SUCCESS_COUNT = "success.count";
        
        private InfrastructureAttributes() {}
    }
    
    /**
     * 后端适配器属性键
     */
    public static final class BackendAttributes {
        public static final String ADAPTER_TYPE = "backend.adapter.type";
        public static final String INSTANCE_NAME = "backend.instance.name";
        public static final String BASE_URL = "backend.base_url";
        public static final String ENDPOINT_PATH = "backend.endpoint.path";
        public static final String RETRY_COUNT = "backend.retry.count";
        public static final String MAX_RETRIES = "backend.max_retries";
        public static final String TIMEOUT = "backend.timeout";
        public static final String CONNECTION_POOL = "backend.connection_pool";
        
        private BackendAttributes() {}
    }
    
    /**
     * 安全属性键
     */
    public static final class SecurityAttributes {
        public static final String USER_ID = "user.id";
        public static final String USER_TYPE = "user.type";
        public static final String AUTH_METHOD = "auth.method";
        public static final String API_KEY_ID = "auth.api_key.id";
        public static final String JWT_SUBJECT = "auth.jwt.subject";
        public static final String SANITIZED_FIELDS = "security.sanitized_fields";
        public static final String SANITIZATION_RULES = "security.sanitization_rules";
        
        private SecurityAttributes() {}
    }
    
    /**
     * 错误属性键
     */
    public static final class ErrorAttributes {
        public static final String ERROR = "error";
        public static final String ERROR_TYPE = "error.type";
        public static final String ERROR_MESSAGE = "error.message";
        public static final String ERROR_STACK = "error.stack";
        public static final String ERROR_CODE = "error.code";
        public static final String RETRY_AFTER = "error.retry_after";
        
        private ErrorAttributes() {}
    }
    
    // ========================================
    // 事件名称常量
    // ========================================
    
    /**
     * 事件名称
     */
    public static final class Events {
        // 请求生命周期事件
        public static final String REQUEST_START = "request.start";
        public static final String REQUEST_END = "request.end";
        public static final String RESPONSE_START = "response.start";
        public static final String RESPONSE_END = "response.end";
        
        // 业务事件
        public static final String LOAD_BALANCER_SELECTION = "load_balancer.selection";
        public static final String RATE_LIMIT_CHECK = "rate_limit.check";
        public static final String CIRCUIT_BREAKER_STATE_CHANGE = "circuit_breaker.state_change";
        public static final String BACKEND_CALL_START = "backend_call.start";
        public static final String BACKEND_CALL_END = "backend_call.end";
        
        // 安全事件
        public static final String AUTHENTICATION_SUCCESS = "auth.success";
        public static final String AUTHENTICATION_FAILURE = "auth.failure";
        public static final String AUTHORIZATION_SUCCESS = "authz.success";
        public static final String AUTHORIZATION_FAILURE = "authz.failure";
        public static final String DATA_SANITIZED = "data.sanitized";
        
        // 错误事件
        public static final String ERROR_OCCURRED = "error.occurred";
        public static final String RETRY_ATTEMPT = "retry.attempt";
        public static final String FALLBACK_EXECUTED = "fallback.executed";
        
        // 配置事件
        public static final String CONFIG_LOADED = "config.loaded";
        public static final String CONFIG_UPDATED = "config.updated";
        public static final String CONFIG_VALIDATION_FAILED = "config.validation_failed";
        
        private Events() {}
    }
    
    // ========================================
    // 标签值常量
    // ========================================
    
    /**
     * 组件名称
     */
    public static final class Components {
        public static final String HTTP_SERVER = "http-server";
        public static final String HTTP_CLIENT = "http-client";
        public static final String LOAD_BALANCER = "load-balancer";
        public static final String RATE_LIMITER = "rate-limiter";
        public static final String CIRCUIT_BREAKER = "circuit-breaker";
        public static final String HEALTH_CHECKER = "health-checker";
        public static final String BACKEND_ADAPTER = "backend-adapter";
        public static final String SECURITY_FILTER = "security-filter";
        public static final String CONFIG_MANAGER = "config-manager";
        public static final String SANITIZER = "sanitizer";
        
        private Components() {}
    }
    
    /**
     * 服务类型
     */
    public static final class ServiceTypes {
        public static final String CHAT = "chat";
        public static final String EMBEDDING = "embedding";
        public static final String RERANK = "rerank";
        public static final String TTS = "tts";
        public static final String STT = "stt";
        public static final String IMAGE_GENERATION = "imgGen";
        public static final String IMAGE_EDIT = "imgEdit";
        
        private ServiceTypes() {}
    }
    
    /**
     * 负载均衡策略
     */
    public static final class LoadBalanceStrategies {
        public static final String RANDOM = "random";
        public static final String ROUND_ROBIN = "round-robin";
        public static final String LEAST_CONNECTIONS = "least-connections";
        public static final String IP_HASH = "ip-hash";
        
        private LoadBalanceStrategies() {}
    }
    
    /**
     * 限流算法
     */
    public static final class RateLimitAlgorithms {
        public static final String TOKEN_BUCKET = "token-bucket";
        public static final String LEAKY_BUCKET = "leaky-bucket";
        public static final String SLIDING_WINDOW = "sliding-window";
        public static final String WARM_UP = "warm-up";
        
        private RateLimitAlgorithms() {}
    }
    
    /**
     * 熔断器状态
     */
    public static final class CircuitBreakerStates {
        public static final String CLOSED = "closed";
        public static final String OPEN = "open";
        public static final String HALF_OPEN = "half-open";
        
        private CircuitBreakerStates() {}
    }
    
    /**
     * 适配器类型
     */
    public static final class AdapterTypes {
        public static final String GPUSTACK = "gpustack";
        public static final String OLLAMA = "ollama";
        public static final String VLLM = "vllm";
        public static final String XINFERENCE = "xinference";
        public static final String LOCALAI = "localai";
        public static final String OPENAI = "openai";
        public static final String NORMAL = "normal";
        
        private AdapterTypes() {}
    }
    
    // ========================================
    // 上下文键常量
    // ========================================
    
    /**
     * 上下文键
     */
    public static final class ContextKeys {
        public static final String TRACING_CONTEXT = "tracing.context";
        public static final String CURRENT_SPAN = "tracing.current_span";
        public static final String TRACE_ID = "tracing.trace_id";
        public static final String SPAN_ID = "tracing.span_id";
        public static final String PARENT_SPAN_ID = "tracing.parent_span_id";
        public static final String SAMPLING_DECISION = "tracing.sampling_decision";
        
        private ContextKeys() {}
    }
    
    // ========================================
    // 头部名称常量
    // ========================================
    
    /**
     * HTTP头部名称
     */
    public static final class Headers {
        // W3C Trace Context
        public static final String TRACEPARENT = "traceparent";
        public static final String TRACESTATE = "tracestate";
        
        // 自定义追踪头部
        public static final String X_TRACE_ID = "X-Trace-Id";
        public static final String X_SPAN_ID = "X-Span-Id";
        public static final String X_PARENT_SPAN_ID = "X-Parent-Span-Id";
        
        // 其他相关头部
        public static final String X_REQUEST_ID = "X-Request-Id";
        public static final String X_CORRELATION_ID = "X-Correlation-Id";
        
        private Headers() {}
    }
    
    private TracingConstants() {}
}