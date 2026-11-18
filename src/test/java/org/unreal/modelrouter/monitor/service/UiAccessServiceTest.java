package org.unreal.modelrouter.monitor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.unreal.modelrouter.monitor.dto.UiPageViewSummary;
import org.unreal.modelrouter.persistence.jpa.entity.UiPageViewEntity;
import org.unreal.modelrouter.persistence.jpa.repository.UiPageViewRepository;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 控制台页面访问统计（B4）服务层测试。
 *
 * <p>重点守四件容易在重构中丢掉的行为：
 * <ol>
 *   <li>上报只写 {@code userId + path + role + 时间}，不采客户端 IP（表里没有这一列，但代码也没处能填）</li>
 *   <li>非法路径（带查询串 / 相对路径 / 协议相对地址 / 超长）一律不写库
 *       ——避免把 token 之类拼进 URL 后被记录</li>
 *   <li>写入失败必须静默：上报失败不能让用户正在做的操作报错</li>
 *   <li>聚合与清理的入参越界要被夹住（days/limit），否则一次请求能把整张表拉出来</li>
 * </ol>
 *
 * @author JAiRouter Team
 * @since 3.3.8
 */
@DisplayName("UiAccessService 测试")
class UiAccessServiceTest {

    private UiPageViewRepository repository;

    private UiAccessService service;

    @BeforeEach
    void setUp() {
        repository = mock(UiPageViewRepository.class);
        service = new UiAccessService(repository);
    }

    @Test
    @DisplayName("上报：写入 userId / path / role 与时间")
    void recordsAccessWithoutClientIp() {
        StepVerifier.create(service.recordAccess("alice", "/call-history/list", "ADMIN"))
                .verifyComplete();

        final ArgumentCaptor<UiPageViewEntity> captor = ArgumentCaptor.forClass(UiPageViewEntity.class);
        verify(repository).save(captor.capture());
        final UiPageViewEntity saved = captor.getValue();
        assertEquals("alice", saved.getUserId());
        assertEquals("/call-history/list", saved.getPath());
        assertEquals("ADMIN", saved.getRole());
        assertNotNull(saved.getViewedAt());
    }

    @Test
    @DisplayName("上报：空主体直接跳过，不写库")
    void ignoresBlankUser() {
        StepVerifier.create(service.recordAccess("  ", "/dashboard/main", "ADMIN")).verifyComplete();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("上报：非法路径一律不写库")
    void ignoresIllegalPaths() {
        final String[] illegal = {"/x?token=1", "/x#frag", "relative/path", "//evil.example.com/x", ""};
        for (final String path : illegal) {
            StepVerifier.create(service.recordAccess("alice", path, "ADMIN")).verifyComplete();
        }
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("上报：超长路径不写库")
    void ignoresTooLongPath() {
        StepVerifier.create(service.recordAccess("alice", "/" + "a".repeat(210), "ADMIN")).verifyComplete();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("上报：角色归一为大写，非法角色丢弃为 null")
    void normalizesRole() {
        StepVerifier.create(service.recordAccess("alice", "/a", "auditor")).verifyComplete();
        final ArgumentCaptor<UiPageViewEntity> captor = ArgumentCaptor.forClass(UiPageViewEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("AUDITOR", captor.getValue().getRole());

        service.recordAccess("alice", "/b", "not a role!").block();
        final ArgumentCaptor<UiPageViewEntity> second = ArgumentCaptor.forClass(UiPageViewEntity.class);
        verify(repository, org.mockito.Mockito.times(2)).save(second.capture());
        assertNull(second.getAllValues().get(1).getRole());
    }

    @Test
    @DisplayName("上报：写库失败静默完成，不向调用方抛错")
    void swallowsWriteFailure() {
        when(repository.save(any())).thenThrow(new IllegalStateException("db down"));
        StepVerifier.create(service.recordAccess("alice", "/dashboard/main", "ADMIN")).verifyComplete();
    }

    @Test
    @DisplayName("聚合：映射四列并与 clamp 后的行数上限一起使用")
    void summarizesRows() {
        when(repository.summarize(any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{"/dashboard/main", "ADMIN", 12L, 2L},
                new Object[]{"/system/page-heat", null, 3L, 1L}));

        final List<UiPageViewSummary> rows = service.summarize(null, null).block();

        assertNotNull(rows);
        assertEquals(2, rows.size());
        assertEquals("/dashboard/main", rows.get(0).path());
        assertEquals("ADMIN", rows.get(0).role());
        assertEquals(12L, rows.get(0).views());
        assertEquals(2L, rows.get(0).users());
        // 角色为 null 的行归一为空串，前端不用再判空
        assertEquals("", rows.get(1).role());

        final ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        final ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).summarize(since.capture(), page.capture());
        assertEquals(20, page.getValue().getPageSize());
        assertTrue(since.getValue().isBefore(LocalDateTime.now()));
    }

    @Test
    @DisplayName("聚合：days/limit 越界被夹到 [1,90] 与 [1,100]")
    void clampsRange() {
        when(repository.summarize(any(), any())).thenReturn(List.of());

        service.summarize(9999, 9999).block();
        final ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        final ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).summarize(since.capture(), page.capture());
        assertEquals(100, page.getValue().getPageSize());
        // 90 天窗口：起点应早于 89 天前
        assertTrue(since.getValue().isBefore(LocalDateTime.now().minusDays(89)));
    }

    @Test
    @DisplayName("清理：非正数保留期不触发删除")
    void skipsPurgeForNonPositiveRetention() {
        assertEquals(0, service.purge(0).block());
        verify(repository, never()).deleteOlderThan(any());
    }

    @Test
    @DisplayName("清理：按 cutoff 删除并返回行数")
    void purgesOldRows() {
        when(repository.deleteOlderThan(any())).thenReturn(42);
        assertEquals(42, service.purge(90).block());
        verify(repository).deleteOlderThan(any());
    }

    @Test
    @DisplayName("路径与角色校验器的边界")
    void validatorsBehave() {
        assertTrue(service.isValidPath("/system/page-heat"));
        assertFalse(service.isValidPath("/x?y=1"));
        assertFalse(service.isValidPath(null));
        assertEquals("ADMIN", service.normalizeRole(" admin "));
        assertNull(service.normalizeRole("1ADMIN"));
        assertNull(service.normalizeRole(null));
    }
}
