#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""docs-version-manager.py 过期判定的离线测试

覆盖“按文档类型解析过期阈值 + ignore_patterns + CLI 覆盖语义”，
不读写仓库中的 docs/docs-versions.json。

用法: python scripts/docs/test_docs_version_manager.py
"""

import importlib.util
import json
import re
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = Path(__file__).resolve().parent / 'docs-version-manager.py'

# 与 docs/docs-version-config.yml 一致的合成配置
SYNTHETIC_CONFIG = {
    'document_scanning': {
        'include_patterns': ['docs/**/*.md', 'README*.md', '*.md'],
        'exclude_patterns': [],
        'document_types': {
            'user_guide': {'patterns': ['docs/zh/getting-started/**', 'docs/en/getting-started/**']},
            'api_reference': {'patterns': ['docs/zh/api-reference/**', 'docs/en/api-reference/**']},
            'configuration': {'patterns': ['docs/zh/configuration/**', 'docs/en/configuration/**']},
            'deployment': {'patterns': ['docs/zh/deployment/**', 'docs/en/deployment/**']},
            'development': {'patterns': ['docs/zh/development/**', 'docs/en/development/**']},
        },
    },
    'outdated_detection': {
        'default_threshold_days': 30,
        'type_thresholds': {
            'api_reference': 14,
            'configuration': 21,
            'user_guide': 30,
            'deployment': 45,
            'development': 90,
        },
        'ignore_patterns': [
            'docs/zh/reference/changelog.md',
            'docs/en/reference/changelog.md',
            'LICENSE*',
        ],
    },
}

CONFIG_YAML = """\
document_scanning:
  document_types:
    configuration:
      patterns: ["docs/en/configuration/**"]
    development:
      patterns: ["docs/en/development/**"]
outdated_detection:
  default_threshold_days: 30
  type_thresholds:
    configuration: 21
    development: 90
  ignore_patterns:
    - "docs/zh/reference/changelog.md"
    - "LICENSE*"
"""

# 文档路径 -> 距今天数
TEMP_DOC_AGES = {
    'docs/en/development/architecture.md': 40,   # 类型阈值 90 -> 不算过期
    'docs/en/configuration/rate-limiting.md': 25,  # 类型阈值 21 -> 过期
    'docs/zh/reference/changelog.md': 500,       # ignore_patterns -> 永不过期
    'LICENSE.md': 500,                           # ignore_patterns (LICENSE*) -> 永不过期
    'docs/en/monitoring/metrics.md': 35,         # 无匹配类型 -> default 30 -> 过期
    'docs/en/tracing/quickstart.md': 25,         # 无匹配类型 -> default 30 -> 不过期
}

CLI_OVERRIDE_EXPECTATIONS = [
    # --check-outdated 参数 -> 期望过期文档数
    (None, 2, '配置驱动：rate-limiting(21d) + metrics(30d)'),
    (20, 3, '覆盖默认阈值 20 天：quickstart(25d) 也过期，development(90d) 仍不过期'),
    (100, 1, '覆盖默认阈值 100 天：无类型文档不再过期，configuration 仍按 21 天'),
]


def load_module():
    # 避免在 scripts/docs/ 下留下 __pycache__
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location('docs_version_manager', SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def make_manager(module, config, doc_ages):
    """用合成 config + versions 构造 manager（不读盘、不写盘）"""
    manager = module.DocumentVersionManager.__new__(module.DocumentVersionManager)
    manager.project_root = REPO_ROOT
    manager.version_file = REPO_ROOT / 'docs' / 'docs-versions.json'
    manager.config_file = REPO_ROOT / 'docs' / 'docs-version-config.yml'
    manager.config = config
    manager.changes = []
    manager.versions = {}

    now = datetime.now()
    for path, days in doc_ages.items():
        manager.versions[path] = module.DocumentVersion(
            path, '1.0.0', (now - timedelta(days=days)).isoformat(), 'deadbeef'
        )
    return manager


def test_document_type_resolution(module):
    manager = make_manager(module, SYNTHETIC_CONFIG, {})

    assert manager.get_document_type('docs/en/development/architecture.md') == 'development'
    assert manager.get_document_type('docs/zh/configuration/kv-cache.md') == 'configuration'
    assert manager.get_document_type('docs/en/monitoring/metrics.md') is None


def test_per_type_thresholds(module):
    ages = {
        'docs/en/development/architecture.md': 30,      # (a) 阈值 90 -> 不过期
        'docs/en/configuration/rate-limiting.md': 25,   # (b) 阈值 21 -> 过期
        'docs/en/monitoring/metrics.md': 35,            # (d) 无类型 -> default 30 -> 过期
        'docs/en/tracing/quickstart.md': 25,            # (d) 无类型且 25 < 30 -> 不过期
    }
    outdated = make_manager(module, SYNTHETIC_CONFIG, ages).check_outdated_documents()

    assert 'docs/en/development/architecture.md' not in outdated, \
        'development 文档 30 天不应过期（类型阈值 90 天）'
    assert 'docs/en/configuration/rate-limiting.md' in outdated, \
        'configuration 文档 25 天应过期（类型阈值 21 天）'
    assert 'docs/en/monitoring/metrics.md' in outdated, \
        '未匹配类型的文档应使用 default_threshold_days=30'
    assert 'docs/en/tracing/quickstart.md' not in outdated, \
        '未匹配类型的文档 25 天不应过期（default 30 天）'
    assert len(outdated) == 2, f'期望 2 个过期文档，实际 {len(outdated)}: {outdated}'


def test_ignore_patterns(module):
    ages = {
        'docs/zh/reference/changelog.md': 500,   # (c) 精确路径
        'docs/en/reference/changelog.md': 500,   # (c) 精确路径
        'LICENSE.md': 500,                       # (c) glob LICENSE*
        'docs/en/configuration/kv-cache.md': 25,  # 对照组，应过期
    }
    manager = make_manager(module, SYNTHETIC_CONFIG, ages)
    outdated = manager.check_outdated_documents()

    for ignored in ('docs/zh/reference/changelog.md', 'docs/en/reference/changelog.md', 'LICENSE.md'):
        assert ignored not in outdated, f'{ignored} 命中 ignore_patterns，不应被报告'
    assert outdated == ['docs/en/configuration/kv-cache.md'], f'期望仅对照组过期，实际 {outdated}'


def test_invalid_date_is_outdated_without_crash(module):
    manager = make_manager(module, SYNTHETIC_CONFIG, {})
    manager.versions['docs/en/configuration/kv-cache.md'] = module.DocumentVersion(
        'docs/en/configuration/kv-cache.md', '1.0.0', 'not-a-date', 'x'
    )
    manager.versions['docs/en/configuration/broken.md'] = module.DocumentVersion(
        'docs/en/configuration/broken.md', '1.0.0', None, 'y'
    )
    outdated = manager.check_outdated_documents()
    assert set(outdated) == {'docs/en/configuration/kv-cache.md', 'docs/en/configuration/broken.md'}, \
        f'无法解析/缺失日期的文档应视为过期且不抛异常，实际 {outdated}'


def test_explicit_threshold_only_overrides_default(module):
    ages = {
        'docs/en/development/architecture.md': 40,     # 类型阈值 90 天，不受覆盖影响
        'docs/en/configuration/rate-limiting.md': 25,  # 类型阈值 21 天，不受覆盖影响
        'docs/en/monitoring/metrics.md': 35,           # 默认阈值，受覆盖影响
        'docs/en/tracing/quickstart.md': 25,           # 默认阈值，受覆盖影响
    }
    manager = make_manager(module, SYNTHETIC_CONFIG, ages)

    outdated_20 = manager.check_outdated_documents(20)
    assert 'docs/en/tracing/quickstart.md' in outdated_20, '显式 20 天应让无类型文档按 20 天判定'
    assert 'docs/en/development/architecture.md' not in outdated_20, '显式阈值不得覆盖类型专属阈值'
    assert 'docs/en/configuration/rate-limiting.md' in outdated_20, '显式阈值不得覆盖类型专属阈值'

    outdated_100 = manager.check_outdated_documents(100)
    assert outdated_100 == ['docs/en/configuration/rate-limiting.md'], \
        f'显式 100 天时仅类型阈值文档过期，实际 {outdated_100}'


def test_cli_forms(tmp_path):
    """CLI 三种调用形式：不带数值 / 带数值覆盖默认阈值"""
    docs_dir = tmp_path / 'docs'
    docs_dir.mkdir(parents=True)
    (docs_dir / 'docs-version-config.yml').write_text(CONFIG_YAML, encoding='utf-8')

    now = datetime.now()
    versions = {
        path: {
            'FilePath': path,
            'Version': '1.0.0',
            'LastModified': (now - timedelta(days=days)).isoformat(),
            'ContentHash': 'deadbeef',
        }
        for path, days in TEMP_DOC_AGES.items()
    }
    (docs_dir / 'docs-versions.json').write_text(
        json.dumps({'versions': versions, 'changes': []}, ensure_ascii=False), encoding='utf-8'
    )

    def run(extra_args):
        result = subprocess.run(
            [sys.executable, str(SCRIPT), '--project-root', str(tmp_path)] + extra_args,
            capture_output=True, text=True, encoding='utf-8', timeout=120
        )
        assert result.returncode == 0, f'CLI 失败: {result.stdout}\n{result.stderr}'
        match = re.search(r'发现 (\d+) 个过期文档', result.stdout)
        assert match, f'输出格式不符合工作流抓取规则（需要 "发现 N 个过期文档"）: {result.stdout}'
        return int(match.group(1)), result.stdout

    for override, expected, why in CLI_OVERRIDE_EXPECTATIONS:
        args = ['--check-outdated'] if override is None else ['--check-outdated', str(override)]
        count, stdout = run(args)
        assert count == expected, f'{" ".join(args)} 期望 {expected} 个（{why}），实际 {count}\n{stdout}'

    _, stdout = run(['--check-outdated'])
    assert '阈值 21 天' in stdout, f'每条过期文档应显示生效阈值: {stdout}'


def main():
    module = load_module()
    tests = [
        ('文档类型识别', lambda: test_document_type_resolution(module)),
        ('按类型阈值判定', lambda: test_per_type_thresholds(module)),
        ('ignore_patterns 生效', lambda: test_ignore_patterns(module)),
        ('无法解析日期视为过期且不崩溃', lambda: test_invalid_date_is_outdated_without_crash(module)),
        ('显式阈值仅覆盖默认阈值', lambda: test_explicit_threshold_only_overrides_default(module)),
    ]

    failures = []
    for name, func in tests:
        try:
            func()
            print(f'✅ {name}')
        except AssertionError as exc:
            failures.append((name, str(exc)))
            print(f'❌ {name}: {exc}')

    with tempfile.TemporaryDirectory() as tmp:
        name = 'CLI --check-outdated 三种形式'
        try:
            test_cli_forms(Path(tmp))
            print(f'✅ {name}')
        except AssertionError as exc:
            failures.append((name, str(exc)))
            print(f'❌ {name}: {exc}')

    total = len(tests) + 1
    if failures:
        print(f'\n❌ {len(failures)}/{total} 个测试失败')
        return 1
    print(f'\n✅ {total}/{total} 个测试通过')
    return 0


if __name__ == '__main__':
    sys.exit(main())
