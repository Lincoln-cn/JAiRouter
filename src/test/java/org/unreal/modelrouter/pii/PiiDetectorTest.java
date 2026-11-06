package org.unreal.modelrouter.pii;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PII 探测器测试.
 *
 * <p>探测器独立于可逆脱敏开关：它只判断「这段文本像不像含个人信息」，
 * 用于拦住「模型自己生成/复述」的身份信息进入缓存。</p>
 */
class PiiDetectorTest {

    private PiiProperties properties;

    private PiiDetector detector;

    @BeforeEach
    void setUp() {
        properties = new PiiProperties();
        properties.getRules().clear();
        detector = new PiiDetector(properties);
        ReflectionTestUtils.invokeMethod(detector, "compile");
    }

    @Test
    @DisplayName("内置规则能识别身份证号")
    void detectsIdCard() {
        assertThat(detector.containsSensitive("我的身份证 110101199003071234 请核对")).isTrue();
    }

    @Test
    @DisplayName("内置规则能识别手机号")
    void detectsMobile() {
        assertThat(detector.containsSensitive("联系电话 13812345678")).isTrue();
    }

    @Test
    @DisplayName("内置规则能识别邮箱")
    void detectsEmail() {
        assertThat(detector.containsSensitive("发到 alice@example.com")).isTrue();
    }

    @Test
    @DisplayName("普通业务文本不误报")
    void plainTextIsNotSensitive() {
        assertThat(detector.containsSensitive("请把这段代码改成 Lambda 写法")).isFalse();
    }

    @Test
    @DisplayName("自定义规则生效")
    void customRuleWorks() {
        PiiProperties.Rule pattern = new PiiProperties.Rule();
        pattern.setName("employee-id");
        pattern.setPattern("\\bEMP-\\d{6}\\b");
        properties.setRules(List.of(pattern));
        ReflectionTestUtils.invokeMethod(detector, "compile");

        assertThat(detector.containsSensitive("工号 EMP-123456")).isTrue();
    }

    @Test
    @DisplayName("空文本不算敏感")
    void blankIsNotSensitive() {
        assertThat(detector.containsSensitive(null)).isFalse();
        assertThat(detector.containsSensitive("")).isFalse();
    }
}
