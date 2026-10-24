package org.unreal.modelrouter.auth.sanitization.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.auth.security.config.properties.SanitizationConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.persistence.jpa.entity.SanitizationConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.SanitizationConfigRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 脱敏配置持久化存储（TA2 / v3.4.0）。
 *
 * <p><b>为什么存配置而不是逐条规则</b>：{@code DefaultSanitizationService} 的规则由
 * {@code SecurityProperties.sanitization} 确定性派生（敏感词 → SENSITIVE_WORD、
 * pii-patterns → PII_PATTERN，ID 从 1 顺序生成）。只要配置快照一致，重启后派生出的规则集与
 * 规则 ID 就一致 —— 逐条存规则既冗余，又会重新引入「规则 ID 在重启后复用/漂移」的问题。</p>
 *
 * <p><b>表的形状</b>：主键是 scope 字符串，当前只保留 {@code response} 一个业务作用域。
 * 历史上的 {@code request} 行与 {@code whitelist_users} 列已在 V5 迁移中删除
 * （入站链路从未消费请求侧脱敏，见 issue #601）。</p>
 *
 * <p><b>装载语义（DB 优先）</b>：response 在库里有行 ⇒ 用该行覆盖内存里的 yml 值；没有行 ⇒ 保留 yml。</p>
 *
 * <p><b>写入语义</b>：整行覆盖，写入在单个事务里完成。</p>
 *
 * <p><b>损坏的快照抛异常，由调用方决定恢复策略</b>：JSON 解析失败会抛 {@link IllegalStateException}，
 * 且必然发生在「覆盖内存配置」之前（先解析、后赋值）。启动期 {@code DefaultSanitizationService}
 * 捕获它并回退到 yml、以 error 级别留痕；运行期保存路径则把它当 500 处理。</p>
 *
 * @author JAiRouter Team
 * @since 3.4.0
 */
@Slf4j
@Component
public class SanitizationConfigStore {

    /** response 作用域 */
    public static final String SCOPE_RESPONSE = "response";

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private static final String SOURCE_DB = "DB";
    private static final String SOURCE_YML = "yml";

    private final SanitizationConfigRepository repository;
    private final ObjectMapper objectMapper;

    public SanitizationConfigStore(final SanitizationConfigRepository repository,
                                   final ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 启动装载：DB 优先覆盖内存配置。
     *
     * @param securityProperties 已由 Spring Boot 绑定完 yml 的安全属性
     * @return response 作用域的来源（供日志与测试断言）
     */
    public LoadResult loadInto(final SecurityProperties securityProperties) {
        final SanitizationConfig config = securityProperties.getSanitization();
        final SanitizationConfigEntity responseRow = repository.findById(SCOPE_RESPONSE).orElse(null);

        final List<String> responseWords = responseRow == null ? null
                : readStringList(responseRow.getSensitiveWords(), SCOPE_RESPONSE, "sensitive_words");
        final List<String> responsePatterns = responseRow == null ? null
                : readStringList(responseRow.getPiiPatterns(), SCOPE_RESPONSE, "pii_patterns");

        final boolean responseFromDatabase = applyResponseRow(
                responseRow, responseWords, responsePatterns, config.getResponse());
        log.info("脱敏配置装载完成：response 来源={}", sourceLabel(responseFromDatabase));
        return new LoadResult(responseFromDatabase);
    }

    /**
     * 把当前内存配置整体覆盖写入数据库。
     *
     * @param securityProperties 当前生效配置（调用方已在内存里应用完本次更新）
     * @param updatedBy         操作者（无认证上下文时为 system）
     */
    @Transactional
    public void persist(final SecurityProperties securityProperties, final String updatedBy) {
        final SanitizationConfig config = securityProperties.getSanitization();
        final LocalDateTime now = LocalDateTime.now();
        repository.save(toResponseEntity(config.getResponse(), now, updatedBy));
        log.info("脱敏配置已落库：response=true，updatedBy={}", updatedBy);
    }

    private boolean applyResponseRow(final SanitizationConfigEntity row,
                                     final List<String> sensitiveWords,
                                     final List<String> piiPatterns,
                                     final SanitizationConfig.ResponseSanitization target) {
        if (row == null) {
            log.info("脱敏配置装载：scope={} 来源={}（数据库无快照）", SCOPE_RESPONSE, SOURCE_YML);
            return false;
        }
        target.setEnabled(row.isEnabled());
        target.setSensitiveWords(sensitiveWords);
        target.setPiiPatterns(piiPatterns);
        target.setMaskingChar(row.getMaskingChar());
        target.setLogSanitization(row.isLogSanitization());
        target.setFailOnError(row.isFailOnError());
        target.setPreserveJsonStructure(row.getPreserveJsonStructure() == null
                || row.getPreserveJsonStructure());
        log.info("脱敏配置装载：scope={} 来源={} updatedAt={} updatedBy={}",
                SCOPE_RESPONSE, SOURCE_DB, row.getUpdatedAt(), row.getUpdatedBy());
        return true;
    }

    private SanitizationConfigEntity toResponseEntity(final SanitizationConfig.ResponseSanitization config,
                                                      final LocalDateTime updatedAt,
                                                      final String updatedBy) {
        return SanitizationConfigEntity.builder()
                .scope(SCOPE_RESPONSE)
                .enabled(config.isEnabled())
                .sensitiveWords(writeStringList(config.getSensitiveWords(), SCOPE_RESPONSE, "sensitive_words"))
                .piiPatterns(writeStringList(config.getPiiPatterns(), SCOPE_RESPONSE, "pii_patterns"))
                .maskingChar(config.getMaskingChar())
                .preserveJsonStructure(config.isPreserveJsonStructure())
                .logSanitization(config.isLogSanitization())
                .failOnError(config.isFailOnError())
                .updatedAt(updatedAt)
                .updatedBy(updatedBy)
                .build();
    }

    private String writeStringList(final List<String> values, final String scope, final String column) {
        final List<String> safeValues = values == null ? List.of() : values;
        try {
            return objectMapper.writeValueAsString(safeValues);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "脱敏配置序列化失败: scope=" + scope + ", column=" + column, e);
        }
    }

    private List<String> readStringList(final String json, final String scope, final String column) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            final List<String> values = objectMapper.readValue(json, STRING_LIST_TYPE);
            return values == null ? new ArrayList<>() : new ArrayList<>(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "脱敏配置反序列化失败: scope=" + scope + ", column=" + column, e);
        }
    }

    private static String sourceLabel(final boolean fromDatabase) {
        return fromDatabase ? SOURCE_DB : SOURCE_YML;
    }

    /**
     * 启动装载结果：response 作用域是否来自数据库（false = 沿用 yml）。
     *
     * @param responseFromDatabase response scope 是否来自 DB
     */
    public record LoadResult(boolean responseFromDatabase) {
    }
}
