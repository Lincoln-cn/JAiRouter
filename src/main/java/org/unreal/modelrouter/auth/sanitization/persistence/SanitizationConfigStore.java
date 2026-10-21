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
 * <p><b>表的形状</b>：一行一个 scope（{@code request} / {@code response}），主键就是 scope 字符串。
 * 两个 scope 的字段集不同，差异列（request 的 {@code whitelist_users}、response 的
 * {@code preserve_json_structure}）可空，空值表示该 scope 没有这个概念。</p>
 *
 * <p><b>装载语义（DB 优先）</b>：某 scope 在库里有行 ⇒ 用该行覆盖内存里的 yml 值；没有行 ⇒ 保留 yml。
 * 两个 scope 独立判定，因此「只有 request 被管理员改过」的库也能正确启动。</p>
 *
 * <p><b>写入语义</b>：只写调用方显式指定的 scope（部分更新），整行覆盖。写入在单个事务里完成，
 * 两个 scope 一起保存时要么都成功、要么都回滚。</p>
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

    /** request 作用域 */
    public static final String SCOPE_REQUEST = "request";

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
     * @return 两个 scope 各自的来源（供日志与测试断言）
     */
    public LoadResult loadInto(final SecurityProperties securityProperties) {
        final SanitizationConfig config = securityProperties.getSanitization();
        final SanitizationConfigEntity requestRow = repository.findById(SCOPE_REQUEST).orElse(null);
        final SanitizationConfigEntity responseRow = repository.findById(SCOPE_RESPONSE).orElse(null);

        // 先把两行的 JSON 列全部解析完，再改任何内存字段：任一行损坏都能整体失败、不留下半更新。
        final List<String> requestWords = requestRow == null ? null
                : readStringList(requestRow.getSensitiveWords(), SCOPE_REQUEST, "sensitive_words");
        final List<String> requestPatterns = requestRow == null ? null
                : readStringList(requestRow.getPiiPatterns(), SCOPE_REQUEST, "pii_patterns");
        final List<String> requestWhitelist = requestRow == null || requestRow.getWhitelistUsers() == null ? null
                : readStringList(requestRow.getWhitelistUsers(), SCOPE_REQUEST, "whitelist_users");
        final List<String> responseWords = responseRow == null ? null
                : readStringList(responseRow.getSensitiveWords(), SCOPE_RESPONSE, "sensitive_words");
        final List<String> responsePatterns = responseRow == null ? null
                : readStringList(responseRow.getPiiPatterns(), SCOPE_RESPONSE, "pii_patterns");

        final boolean requestFromDatabase = applyRequestRow(
                requestRow, requestWords, requestPatterns, requestWhitelist, config.getRequest());
        final boolean responseFromDatabase = applyResponseRow(
                responseRow, responseWords, responsePatterns, config.getResponse());
        log.info("脱敏配置装载完成：request 来源={}，response 来源={}",
                sourceLabel(requestFromDatabase), sourceLabel(responseFromDatabase));
        return new LoadResult(requestFromDatabase, responseFromDatabase);
    }

    /**
     * 把当前内存配置整体覆盖写入数据库（只写显式指定的 scope）。
     *
     * <p>事务边界在本方法：两个 scope 的写入共用同一个事务，任一失败不会留下「只写了一半」的库。</p>
     *
     * @param securityProperties 当前生效配置（调用方已在内存里应用完本次更新）
     * @param persistRequest    是否持久化 request scope
     * @param persistResponse   是否持久化 response scope
     * @param updatedBy         操作者（无认证上下文时为 system）
     */
    @Transactional
    public void persist(final SecurityProperties securityProperties,
                        final boolean persistRequest,
                        final boolean persistResponse,
                        final String updatedBy) {
        final SanitizationConfig config = securityProperties.getSanitization();
        final LocalDateTime now = LocalDateTime.now();
        if (persistRequest) {
            repository.save(toRequestEntity(config.getRequest(), now, updatedBy));
        }
        if (persistResponse) {
            repository.save(toResponseEntity(config.getResponse(), now, updatedBy));
        }
        log.info("脱敏配置已落库：request={}，response={}，updatedBy={}",
                persistRequest, persistResponse, updatedBy);
    }

    private boolean applyRequestRow(final SanitizationConfigEntity row,
                                    final List<String> sensitiveWords,
                                    final List<String> piiPatterns,
                                    final List<String> whitelistUsers,
                                    final SanitizationConfig.RequestSanitization target) {
        if (row == null) {
            log.info("脱敏配置装载：scope={} 来源={}（数据库无快照）", SCOPE_REQUEST, SOURCE_YML);
            return false;
        }
        target.setEnabled(row.isEnabled());
        target.setSensitiveWords(sensitiveWords);
        target.setPiiPatterns(piiPatterns);
        target.setMaskingChar(row.getMaskingChar());
        target.setWhitelistUsers(whitelistUsers == null ? new ArrayList<>() : whitelistUsers);
        target.setLogSanitization(row.isLogSanitization());
        target.setFailOnError(row.isFailOnError());
        log.info("脱敏配置装载：scope={} 来源={} updatedAt={} updatedBy={}",
                SCOPE_REQUEST, SOURCE_DB, row.getUpdatedAt(), row.getUpdatedBy());
        return true;
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

    private SanitizationConfigEntity toRequestEntity(final SanitizationConfig.RequestSanitization config,
                                                     final LocalDateTime updatedAt,
                                                     final String updatedBy) {
        return SanitizationConfigEntity.builder()
                .scope(SCOPE_REQUEST)
                .enabled(config.isEnabled())
                .sensitiveWords(writeStringList(config.getSensitiveWords(), SCOPE_REQUEST, "sensitive_words"))
                .piiPatterns(writeStringList(config.getPiiPatterns(), SCOPE_REQUEST, "pii_patterns"))
                .maskingChar(config.getMaskingChar())
                .whitelistUsers(writeStringList(config.getWhitelistUsers(), SCOPE_REQUEST, "whitelist_users"))
                .logSanitization(config.isLogSanitization())
                .failOnError(config.isFailOnError())
                .updatedAt(updatedAt)
                .updatedBy(updatedBy)
                .build();
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
     * 启动装载结果：两个 scope 各自是否来自数据库（false = 沿用 yml）。
     *
     * @param requestFromDatabase  request scope 是否来自 DB
     * @param responseFromDatabase response scope 是否来自 DB
     */
    public record LoadResult(boolean requestFromDatabase, boolean responseFromDatabase) {
    }
}
