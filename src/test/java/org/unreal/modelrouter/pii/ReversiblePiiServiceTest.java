package org.unreal.modelrouter.pii;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.monitor.callhistory.crypto.RecordContentCipher;
import org.unreal.modelrouter.persistence.jpa.entity.PiiTokenVaultEntity;
import org.unreal.modelrouter.persistence.jpa.repository.PiiTokenVaultRepository;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 可逆脱敏服务的边界（#987）。
 *
 * <p>重点不是「能不能替换」，而是三件容易做错的事：</p>
 * <ol>
 *   <li>模型改写占位符时必须原样保留，<b>不能猜</b>；</li>
 *   <li>开关关闭时连文本都不该解析（避免无谓开销与意外改写）；</li>
 *   <li>过期映射不参与还原。</li>
 * </ol>
 */
class ReversiblePiiServiceTest {

    private PiiTokenVaultRepository repository;

    private RecordContentCipher cipher;

    private PiiProperties properties;

    private final List<PiiTokenVaultEntity> saved = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(PiiTokenVaultRepository.class);
        cipher = mock(RecordContentCipher.class);
        properties = new PiiProperties();
        properties.setEnabled(true);
        saved.clear();

        when(cipher.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(cipher.decrypt(anyString())).thenAnswer(inv -> {
            final String value = inv.getArgument(0);
            return value.startsWith("enc:") ? value.substring(4) : value;
        });
        when(repository.saveAll(any())).thenAnswer(inv -> {
            final List<PiiTokenVaultEntity> list = inv.getArgument(0);
            saved.addAll(list);
            return list;
        });
    }

    @Test
    @DisplayName("命中手机号时替换为占位符，并把密文写进 vault")
    void tokenizeReplacesAndPersists() {
        final ReversiblePiiService service = new ReversiblePiiService(repository, cipher, properties);

        StepVerifier.create(service.tokenize("scope-1", "联系我 13800138000 谢谢"))
                .assertNext(result -> assertThat(result).isEqualTo("联系我 [PII_1] 谢谢"))
                .verifyComplete();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPlaceholder()).isEqualTo("[PII_1]");
        assertThat(saved.get(0).getCiphertext()).isEqualTo("enc:13800138000");
        assertThat(saved.get(0).getRuleName()).isEqualTo("mobile");
        assertThat(saved.get(0).getScopeId()).isEqualTo("scope-1");
    }

    @Test
    @DisplayName("回包里出现占位符时还原成原文")
    void detokenizeRestores() {
        when(repository.findByScopeIdAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of(entry("[PII_1]", "enc:13800138000")));
        final ReversiblePiiService service = new ReversiblePiiService(repository, cipher, properties);

        StepVerifier.create(service.detokenize("scope-1", "已记录：[PII_1]"))
                .assertNext(result -> assertThat(result).isEqualTo("已记录：13800138000"))
                .verifyComplete();
    }

    @Test
    @DisplayName("模型把占位符改写了就原样保留，绝不猜测还原")
    void mangledPlaceholderIsLeftUntouched() {
        when(repository.findByScopeIdAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of(entry("[PII_1]", "enc:13800138000")));
        final ReversiblePiiService service = new ReversiblePiiService(repository, cipher, properties);

        StepVerifier.create(service.detokenize("scope-1", "已记录：pii_1 和 [PII_1 ]"))
                .assertNext(result -> assertThat(result).isEqualTo("已记录：pii_1 和 [PII_1 ]"))
                .verifyComplete();
    }

    @Test
    @DisplayName("开关关闭时不解析文本、也不写 vault")
    void disabledMeansNoop() {
        properties.setEnabled(false);
        final ReversiblePiiService service = new ReversiblePiiService(repository, cipher, properties);

        StepVerifier.create(service.tokenize("scope-1", "13800138000"))
                .assertNext(result -> assertThat(result).isEqualTo("13800138000"))
                .verifyComplete();

        verify(repository, never()).saveAll(any());
    }

    @Test
    @DisplayName("没有命中任何规则时文本原样返回")
    void unmatchedTextIsReturnedAsIs() {
        final ReversiblePiiService service = new ReversiblePiiService(repository, cipher, properties);

        StepVerifier.create(service.tokenize("scope-1", "今天天气不错"))
                .assertNext(result -> assertThat(result).isEqualTo("今天天气不错"))
                .verifyComplete();

        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("多条规则命中时按规则名顺序编号，编号连续不重复")
    void multipleRulesGetDistinctPlaceholders() {
        final ReversiblePiiService service = new ReversiblePiiService(repository, cipher, properties);

        StepVerifier.create(service.tokenize("scope-1", "13800138000 和 a@b.com"))
                .assertNext(result -> {
                    assertThat(result).contains("[PII_1]").contains("[PII_2]");
                    assertThat(result).doesNotContain("13800138000").doesNotContain("a@b.com");
                })
                .verifyComplete();

        assertThat(saved).hasSize(2);
        assertThat(saved.stream().map(PiiTokenVaultEntity::getPlaceholder).distinct().count()).isEqualTo(2);
    }

    @Test
    @DisplayName("过期映射不参与还原（仓储层已按 expiresAt 过滤）")
    void expiredEntriesAreNotUsed() {
        when(repository.findByScopeIdAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());
        final ReversiblePiiService service = new ReversiblePiiService(repository, cipher, properties);

        StepVerifier.create(service.detokenize("scope-1", "已记录：[PII_1]"))
                .assertNext(result -> assertThat(result).isEqualTo("已记录：[PII_1]"))
                .verifyComplete();
    }

    @Test
    @DisplayName("还原时解密失败按占位符原样返回，不抛给调用方")
    void decryptFailureIsSwallowed() {
        when(repository.findByScopeIdAndExpiresAtAfter(anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of(entry("[PII_1]", "broken")));
        when(cipher.decrypt("broken")).thenThrow(new IllegalStateException("解密失败"));
        final ReversiblePiiService service = new ReversiblePiiService(repository, cipher, properties);

        StepVerifier.create(service.detokenize("scope-1", "已记录：[PII_1]"))
                .assertNext(result -> assertThat(result).isEqualTo("已记录：[PII_1]"))
                .verifyComplete();
    }

    @Test
    @DisplayName("内置规则默认启用，可关闭")
    void builtinRulesCanBeDisabled() {
        properties.setBuiltinRulesEnabled(false);
        final ReversiblePiiService service = new ReversiblePiiService(repository, cipher, properties);
        assertThat(service.ruleNames()).isEmpty();

        properties.setBuiltinRulesEnabled(true);
        final ReversiblePiiService enabledService =
                new ReversiblePiiService(repository, cipher, properties);
        assertThat(enabledService.ruleNames()).containsExactly("bank-card", "email", "id-card", "mobile");
    }

    @Test
    @DisplayName("TTL 写入 expires_at")
    void expiresAtUsesTtl() {
        properties.setTtl(Duration.ofMinutes(5));
        final ReversiblePiiService service = new ReversiblePiiService(repository, cipher, properties);

        StepVerifier.create(service.tokenize("scope-1", "13800138000")).expectNextCount(1).verifyComplete();

        final PiiTokenVaultEntity entity = saved.get(0);
        assertThat(Duration.between(entity.getCreatedAt(), entity.getExpiresAt())).isEqualTo(Duration.ofMinutes(5));
    }

    private static PiiTokenVaultEntity entry(String placeholder, String ciphertext) {
        return PiiTokenVaultEntity.builder()
                .scopeId("scope-1")
                .placeholder(placeholder)
                .ciphertext(ciphertext)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }
}
