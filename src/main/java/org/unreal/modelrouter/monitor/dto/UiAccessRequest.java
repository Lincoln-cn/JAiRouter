package org.unreal.modelrouter.monitor.dto;

/**
 * 控制台页面访问上报（B4，2026-11-18）。
 *
 * <p>前端在每次路由切换后上报「当前用户看了哪一页」，用于回答两个产品问题：
 * 哪些页面真的有人用、哪些角色的入口形同虚设。设计约束：
 * <ul>
 *   <li><b>只记 userId 与页面</b>：不透传查询参数、不记客户端 IP（用户明确要求只看「谁看过哪一页」）</li>
 *   <li><b>页面标识用路由路径</b>：稳定、可直接映射回菜单与页面组件，避免前端改标题就断档</li>
 *   <li><b>角色随上报带上</b>：审计表的 {@code details} 列存角色名，便于按角色×页面聚合
 *       （审计表没有独立的 role 列，且不改表结构）</li>
 * </ul>
 *
 * @param path 路由路径（必须以 {@code /} 开头，不含查询串与 hash）
 * @param role 角色名（ADMIN/OPERATOR/USER/VIEWER/AUDITOR/...），未知时可为空
 * @author JAiRouter Team
 * @since 3.3.8
 */
public record UiAccessRequest(String path, String role) {
}
