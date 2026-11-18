package org.unreal.modelrouter.monitor.dto;

/**
 * 页面访问热度的一行（B4）。
 *
 * @param path  路由路径
 * @param role  角色名（上报时带上的角色，用于「按角色看谁真的在用」）
 * @param views 访问次数
 * @param users 去重后的用户数
 * @author JAiRouter Team
 * @since 3.3.8
 */
public record UiPageViewSummary(String path, String role, long views, long users) {
}
