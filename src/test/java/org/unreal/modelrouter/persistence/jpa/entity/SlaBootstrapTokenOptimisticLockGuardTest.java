package org.unreal.modelrouter.persistence.jpa.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 接入令牌「只能使用一次」所依赖的乐观锁必须留在实体上（#1210 / #1221）。
 *
 * <p>一次性语义不是靠先查后写的应用层判断守住的 —— 那是并发下必然失效的写法。它靠
 * {@link SlaBootstrapTokenEntity} 上的 {@code @Version}：两个并发消费同一令牌时，后提交的那个
 * 会拿到 {@code ObjectOptimisticLockingFailureException}，服务层再把它翻译成 401。</p>
 *
 * <p>而 {@code SlaAccessServiceTest.rejectsConcurrentDoubleSpend} 只是 mock 出那个异常来验证
 * 「异常 → 401」的翻译，把 {@code @Version} 删掉它照样绿。也就是说，**乐观锁本身没有调用方
 * 能测的运行时痕迹**（JPA 在提交时才发现版本冲突），只能从实体定义上守。所以这里直接读源码断言。</p>
 *
 * <p>这是源码级断言，不是像素级洁癖：本仓 `AdminBlockingOffloadFilterTest`、
 * `AuditEventTypeMigrationGuardTest` 都是同一手法（读源码/读脚本，断言结构性约束还在）。</p>
 *
 * @author JAiRouter Team
 */
@DisplayName("SLA 接入令牌：一次性语义依赖的乐观锁不能被摘掉")
class SlaBootstrapTokenOptimisticLockGuardTest {

    /** 实体源码路径（模块根下，surefire 的工作目录即模块根）。 */
    private static final Path ENTITY_SOURCE = Paths.get(
            "src", "main", "java", "org", "unreal", "modelrouter",
            "persistence", "jpa", "entity", "SlaBootstrapTokenEntity.java");

    private static String entitySource() throws Exception {
        assertTrue(Files.exists(ENTITY_SOURCE), "找不到实体源码：" + ENTITY_SOURCE.toAbsolutePath());
        return Files.readString(ENTITY_SOURCE, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("实体保留了 @Version 乐观锁字段")
    void keepsVersionColumn() throws Exception {
        final String source = entitySource();

        assertTrue(source.contains("@Version"),
                "SlaBootstrapTokenEntity 上的 @Version 被移除了：并发消费同一令牌将变成两次都成功，"
                        + "「只能使用一次」不再是硬保证（#1210）");
        // 光有 "@Version" 字样不够（javadoc 里也会提到"乐观锁"），要落到真的字段声明上
        final int annotationAt = source.indexOf("@Version");
        final String afterAnnotation = source.substring(annotationAt, Math.min(source.length(), annotationAt + 300));
        assertTrue(afterAnnotation.contains("private Long version"),
                "@Version 之后不是 version 字段声明：注解可能被移到了别处或注释里");
    }

    @Test
    @DisplayName("反空转：同一读法确实读到了实体内容")
    void scannerActuallyReadsEntity() throws Exception {
        final String source = entitySource();

        // 若上面两条断言因为「读到空文件/读错文件」而侥幸通过，这里会先红。
        assertTrue(source.contains("class SlaBootstrapTokenEntity"),
                "读到的不是 SlaBootstrapTokenEntity 的源码");
        assertTrue(source.contains("tokenHash"),
                "源码里找不到 tokenHash 字段，说明解析路径或文件内容不符合预期");
    }
}
