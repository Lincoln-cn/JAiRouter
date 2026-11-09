package org.unreal.modelrouter.auth.security.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * issue #1062：返回 {@code Mono} 的方法不能标方法级 {@code @Transactional}。
 *
 * <p>这类注解在方法返回时即提交，而实际写入发生在订阅后的 {@code boundedElastic} 线程，
 * 注解覆盖不到 —— 它不会出错，只会让人误以为「这段写入有事务保护」。这条测试是防回归守卫：
 * 一旦有人把注解加回来就会红。</p>
 */
@DisplayName("ExtendedSecurityAuditServiceImpl 事务注解守卫（issue #1062）")
class ExtendedSecurityAuditServiceImplTransactionTest {

    @Test
    @DisplayName("没有任何返回 Mono 的方法被标 @Transactional")
    void reactiveMethodsMustNotCarryMethodLevelTransactional() {
        for (Method method : ExtendedSecurityAuditServiceImpl.class.getDeclaredMethods()) {
            if (!Mono.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            assertNull(method.getAnnotation(Transactional.class),
                    "方法 " + method.getName() + " 返回 Mono，方法级 @Transactional 覆盖不到订阅阶段的写入；"
                            + "需要原子性请用 TransactionalOperator");
        }
    }
}
