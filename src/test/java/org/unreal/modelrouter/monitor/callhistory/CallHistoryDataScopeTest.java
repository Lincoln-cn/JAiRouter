/*
 * Copyright (c) 2025 JAiRouter Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.monitor.callhistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.unreal.modelrouter.auth.security.permission.DataScope;
import org.unreal.modelrouter.auth.security.permission.DataScopeResolver;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryQueryDTO;
import org.unreal.modelrouter.persistence.jpa.entity.ApiCallHistoryEntity;

/**
 * 调用历史的数据面范围测试（本人读）。
 *
 * <p>守的是三件事：</p>
 * <ol>
 *   <li>受限主体的查询必须带上服务端解析出来的范围，前端传的 userId 不得生效；</li>
 *   <li>详情接口对不在范围内的记录按「不存在」返回，不能用 403 泄露存在性；</li>
 *   <li>链路查询按范围过滤，不能因为知道 traceId 就看到别人的记录。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("调用历史：本人读的数据面范围")
class CallHistoryDataScopeTest {

    @Mock
    private ApiCallHistoryService callHistoryService;

    @Mock
    private ApiCallHistoryRecorder callHistoryRecorder;

    @Mock
    private SecurityAuditService securityAuditService;

    @Mock
    private DataScopeResolver dataScopeResolver;

    @InjectMocks
    private ApiCallHistoryController controller;

    private static UsernamePasswordAuthenticationToken user(final String name) {
        return new UsernamePasswordAuthenticationToken(name, "n/a",
                List.of(new SimpleGrantedAuthority("callhistory:self:read")));
    }

    @Test
    @DisplayName("受限主体查询：服务端范围覆盖前端 userId")
    void queryUsesServerSideScope() {
        final DataScope scope = DataScope.of("bob").withApiKeyIds(Set.of("key-bob"));
        when(dataScopeResolver.resolveWithKeys(any(), any(), any())).thenReturn(reactor.core.publisher.Mono.just(scope));
        when(callHistoryService.query(any(), any())).thenReturn(Page.<ApiCallHistoryEntity>empty());

        final CallHistoryQueryDTO dto = new CallHistoryQueryDTO();
        dto.setUserId("alice");

        controller.query(dto, user("bob")).block();

        final ArgumentCaptor<DataScope> captor = ArgumentCaptor.forClass(DataScope.class);
        verify(callHistoryService).query(eq(dto), captor.capture());
        assertThat(captor.getValue().restricted()).isTrue();
        assertThat(captor.getValue().userId()).isEqualTo("bob");
        assertThat(captor.getValue().apiKeyIds()).containsExactly("key-bob");
    }

    @Test
    @DisplayName("详情：不在范围内的记录按不存在返回")
    void detailOutsideScopeIsNotFound() {
        when(dataScopeResolver.resolveWithKeys(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Mono.just(DataScope.of("bob")));
        final ApiCallHistoryEntity someoneElse = ApiCallHistoryEntity.builder()
                .id(7L).userId("alice").apiKeyId("key-alice").recordLevel("METADATA_ONLY").build();
        when(callHistoryService.findById(7L)).thenReturn(java.util.Optional.of(someoneElse));

        final var response = controller.getDetail(7L, user("bob")).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("详情：范围内（密钥属于本人）可以读到")
    void detailInsideScopeByKeyIsVisible() {
        when(dataScopeResolver.resolveWithKeys(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Mono.just(DataScope.of("bob").withApiKeyIds(Set.of("key-bob"))));
        final ApiCallHistoryEntity mine = ApiCallHistoryEntity.builder()
                .id(8L).userId("carol").apiKeyId("key-bob").recordLevel("METADATA_ONLY").build();
        when(callHistoryService.findById(8L)).thenReturn(java.util.Optional.of(mine));

        final var response = controller.getDetail(8L, user("bob")).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("链路：不属于本人的记录被过滤掉")
    void traceFiltersForeignRecords() {
        when(dataScopeResolver.resolveWithKeys(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Mono.just(DataScope.of("bob")));
        when(callHistoryService.findByTraceId("trace-1")).thenReturn(List.of(
                ApiCallHistoryEntity.builder().id(1L).userId("bob").recordLevel("METADATA_ONLY").build(),
                ApiCallHistoryEntity.builder().id(2L).userId("alice").recordLevel("METADATA_ONLY").build()));

        final var response = controller.findByTraceId("trace-1", user("bob")).block();

        assertThat(response).isNotNull();
        @SuppressWarnings("unchecked")
        final var body = (org.unreal.modelrouter.common.controller.response.RouterResponse<List<ApiCallHistoryEntity>>)
                response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getData()).hasSize(1);
        assertThat(body.getData().get(0).getUserId()).isEqualTo("bob");
    }

    @Test
    @DisplayName("不受限主体：范围为空且不查密钥")
    void unrestrictedScopePassesThrough() {
        when(dataScopeResolver.resolveWithKeys(any(), any(), any()))
                .thenReturn(reactor.core.publisher.Mono.just(DataScope.unrestricted()));
        when(callHistoryService.query(any(), any())).thenReturn(new PageImpl<>(List.of()));

        controller.query(new CallHistoryQueryDTO(), user("bob")).block();

        final ArgumentCaptor<DataScope> captor = ArgumentCaptor.forClass(DataScope.class);
        verify(callHistoryService).query(any(), captor.capture());
        assertThat(captor.getValue().restricted()).isFalse();
    }
}
