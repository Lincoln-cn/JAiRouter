#!/bin/bash
# Git 标签合并方案
# 将v2.x版本号整理为v2.0-v2.5区间

echo "=== Git 标签合并方案 ==="
echo ""
echo "当前标签数量统计:"
echo "- v2.1.x系列: 4个 → 合并为 v2.1.0, v2.1.1, v2.1.2"
echo "- v2.2.x系列: 10个 → 合并为 v2.2.0-v2.2.9"
echo "- v2.3.x系列: 8个 → 合并为 v2.3.0, v2.3.1, v2.3.2, v2.3.3"
echo "- v2.4.x系列: 8个 → 合并为 v2.4.0-v2.4.7"
echo "- v2.5.x系列及之后: ~120个 → 整理为 v2.5.0-v2.5.x"
echo ""
echo "=== 合并映射表 ==="
echo ""

# 合并方案映射表
cat << 'EOF'
## 版本合并映射表

| 新版本号 | 内容 | 对应旧版本 |
|----------|------|-----------|
| **v2.0.x** | 基础架构 | v2.1.2之前的版本 |
| v2.0.0 | 初始稳定版 | v2.1.2 |
| v2.0.1 | 早期修复 | v2.1.3, v2.1.4 |

| **v2.1.x** | 核心功能完善 | v2.2.x系列 |
| v2.1.0 | Chat/Embedding基础 | v2.2.0 |
| v2.1.1 | 功能增强 | v2.2.1-v2.2.4 |
| v2.1.2 | 追踪集成 | v2.2.5 |
| v2.1.3 | 监控完善 | v2.2.6-v2.2.9 |

| **v2.2.x** | Package重组准备 | v2.3.x-v2.6.x |
| v2.2.0 | Checkstyle质量提升 | v2.3.0, v2.3.1 |
| v2.2.1 | 配置优化 | v2.3.2, v2.3.3 |
| v2.2.2 | 功能稳定 | v2.4.x系列 |
| v2.2.3 | 指标优化 | v2.5.x系列(前半) |
| v2.2.4 | 网关优化 | v2.5.x系列(后半) |
| v2.2.5 | Checkstyle修复 | v2.6.x系列 |

| **v2.3.x** | Package重组+配置整合 | v2.7.x-v2.9.x |
| v2.3.0 | Auth模块迁移 | v2.7.0, v2.7.1 |
| v2.3.1 | Config模块迁移 | v2.7.2 |
| v2.3.2 | Router模块迁移 | v2.7.3, v2.7.4 |
| v2.3.3 | Monitor模块迁移 | v2.7.5, v2.7.6 |
| v2.3.4 | Common模块迁移 | v2.7.7, v2.7.8 |
| v2.3.5 | 配置文件整合 | v2.8.x系列 |
| v2.3.6 | 技术债务修复 | v2.9.x系列 |

| **v2.4.x** | DTO重构+大类拆分Phase1 | v2.10.x-v2.16.x |
| v2.4.0 | JwtTokenController重构 | v2.10.x, v2.11.x |
| v2.4.1 | TracingService重构 | v2.12.x, v2.13.x |
| v2.4.2 | ApiKeyService重构 | v2.14.x, v2.15.x |
| v2.4.3 | ConfigurationHelper重构 | v2.16.x |
| v2.4.4 | 日志DTO重构 | v2.16.x系列 |

| **v2.5.x** | ConfigurationService拆分+BaseAdapter拆分 | v2.17.x-v2.27.x |
| v2.5.0 | ConfigurationService拆分开始 | v2.17.x-v2.20.x |
| v2.5.1 | TracingConfigManager提取 | v2.22.x |
| v2.5.2 | ConfigComparisonService提取 | v2.23.x |
| v2.5.3 | 死代码清理 | v2.24.x, v2.25.x |
| v2.5.4 | BaseAdapter Phase1 | v2.26.x |
| v2.5.5 | BaseAdapter Phase2+3 | v2.27.x(当前) |

EOF

echo ""
echo "=== 执行步骤 ==="
echo ""
echo "方案A: 删除旧标签，创建新标签（推荐）"
echo "  优点: 版本号清晰"
echo "  缺点: 丢失历史标签引用"
echo ""
echo "方案B: 保留所有标签，仅添加新标签别名"
echo "  优点: 不丢失历史"
echo "  缺点: 标签数量更多"
echo ""
echo "方案C: 仅删除明显的冗余标签"
echo "  优点: 平衡清晰和历史"
echo "  缺点: 需要手动判断"
echo ""
echo "=== 建议方案 ==="
echo "采用方案C: 删除以下明显冗余的标签:"
echo ""
echo "# 删除奇怪编号的标签"
echo "git tag -d v2.3.1.1 v2.3.1.2 v2.3.1.3 v2.3.1.4"
echo "git tag -d v2.5.3.1 v2.5.3.2 v2.5.3.3 v2.5.3.4 v2.5.3.5 v2.5.3.6 v2.5.3.7 v2.5.3.8 v2.5.3.9"
echo "git tag -d v2.9.6.2 v2.9.6.3 v2.9.6.5 v2.9.14 v2.9.15 v2.9.16 v2.9.17 v2.9.19"
echo ""
echo "# 删除-final标签（保留x-final作为系列结束标记）"
echo "git tag -d v2.3.0-final"
echo ""
echo "# 删除-plan标签"
echo "git tag -d v2.3.3-plan"
echo ""
echo "# 删除alpha标签（开发中版本）"
echo "git tag -d v2.27.0-alpha"
echo ""
echo "保留以下重要里程碑标签:"
echo "- v2.1.2, v2.2.0-v2.2.9"
echo "- v2.3.0, v2.3.1, v2.3.2, v2.3.3"
echo "- v2.4.0-v2.4.7"
echo "- v2.5.1-v2.5.6 (精选)"
echo "- v2.6.x-final, v2.7.x-final, v2.9.x-final, v2.10.x-final等系列结束标记"
echo "- v2.20.0-v2.27.0 (当前开发版本)"