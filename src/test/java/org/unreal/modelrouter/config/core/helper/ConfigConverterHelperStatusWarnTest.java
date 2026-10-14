package org.unreal.modelrouter.config.core.helper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.common.constants.InstanceStatus;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #368：配置文件路径的 {@code status} **有意不拒绝**取值（运维可能有意写 {@code inactive}），
 * 但取值不在词表内时**必须告警** —— 否则一个拼写错误就会让实例被可用性过滤静默排除，
 * 现象与 #354 修复前一致（客户端拿到误导性的 404「找不到该模型」）。
 */
@DisplayName("ConfigConverterHelper：配置路径 status 词表告警（issue #368）")
class ConfigConverterHelperStatusWarnTest {

    private static Map<String, Object> instanceMap(final String status) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "inst-1");
        map.put("baseUrl", "http://127.0.0.1:18099");
        map.put("path", "/v1/chat/completions");
        if (status != null) {
            map.put("status", status);
        }
        return map;
    }

    private static ModelRouterProperties.ModelInstance convert(final Map<String, Object> map) {
        return new ConfigConverterHelper().convertMapToInstance(map);
    }

    @Test
    @DisplayName("status='activ'（拼写错误）⇒ 不改写取值，但 WARN 点名该取值与合法词表")
    void unknownStatus_logsWarnAndKeepsRawValue() {
        Logger logger = (Logger) LoggerFactory.getLogger(ConfigConverterHelper.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ModelRouterProperties.ModelInstance instance = convert(instanceMap("activ"));

            assertEquals("activ", instance.getStatus(),
                    "配置路径有意不拒绝取值（只告警），不得擅自改写");
            assertTrue(appender.list.stream()
                            .anyMatch(e -> e.getLevel() == Level.WARN
                                    && e.getFormattedMessage().contains("activ")
                                    && e.getFormattedMessage().contains("ACTIVE")),
                    "必须告警、且消息里点明非法取值与合法词表：" + appender.list);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("status='inactive' / 'ACTIVE' 等合法值 ⇒ 不告警（取值原样保留，判据大小写不敏感）")
    void allowedStatus_doesNotWarn() {
        Logger logger = (Logger) LoggerFactory.getLogger(ConfigConverterHelper.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            // 配置路径**有意不改写取值**（只校验词表），所以断言的是「判据结果」而不是规范值
            assertEquals("inactive", convert(instanceMap("inactive")).getStatus());
            assertTrue(!InstanceStatus.isActive("inactive"));

            assertEquals("ACTIVE", convert(instanceMap("ACTIVE")).getStatus());
            assertTrue(InstanceStatus.isActive("ACTIVE"));

            // 缺省：ModelInstance 自带默认值（小写 active），判据同样认它
            assertTrue(InstanceStatus.isActive(convert(instanceMap(null)).getStatus()));

            assertTrue(appender.list.stream().noneMatch(e -> e.getLevel() == Level.WARN),
                    "合法取值与缺省不得产生 WARN：" + appender.list);
        } finally {
            logger.detachAppender(appender);
        }
    }
}
