#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""docs-version-manager.py 过期判定的离线测试

覆盖“按文档类型解析过期阈值 + ignore_patterns + CLI 覆盖语义 + 人工评审 ReviewedAt”，
不读写仓库中的 docs/docs-versions.json（需要写盘的用例一律在临时项目根内进行）。

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

# 必须在导入被测脚本之前设置，避免留下 scripts/docs/__pycache__
sys.dont_write_bytecode = True

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


# ---------------------------------------------------------------- 人工评审 ReviewedAt

TEMP_REVIEW_DOCS = {
    'docs/en/configuration/already-reviewed.md': 40,  # 40 天未改 + 近期评审 -> 不算过期
    'docs/en/configuration/never-reviewed.md': 40,    # 40 天未改且无评审 -> 过期
    'docs/en/configuration/review-expired.md': 40,    # 评审本身已超阈值 -> 重新过期
}


def write_temp_project(tmp_path):
    """构建临时项目根（真实仓库以外的沙箱），返回临时 docs-versions.json 路径"""
    docs_dir = tmp_path / 'docs'
    docs_dir.mkdir(parents=True)
    (docs_dir / 'docs-version-config.yml').write_text(CONFIG_YAML, encoding='utf-8')

    for rel_path in TEMP_REVIEW_DOCS:
        target = tmp_path / rel_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(f'# {rel_path}\n\n临时测试文档\n', encoding='utf-8')

    return docs_dir / 'docs-versions.json'


def run_cli(tmp_path, extra_args, expect_success=True):
    result = subprocess.run(
        [sys.executable, str(SCRIPT), '--project-root', str(tmp_path)] + extra_args,
        capture_output=True, text=True, encoding='utf-8', timeout=180
    )
    if expect_success:
        assert result.returncode == 0, f'CLI 失败 {extra_args}: {result.stdout}\n{result.stderr}'
    return result


def read_versions(versions_json):
    return json.loads(versions_json.read_text(encoding='utf-8'))['versions']


def test_recent_review_keeps_document_in_window(module):
    path = 'docs/en/configuration/rate-limiting.md'  # configuration 阈值 21 天
    manager = make_manager(module, SYNTHETIC_CONFIG, {path: 25})

    assert manager.check_outdated_documents() == [path], '前提：25 天 > 阈值 21 天，本应过期'

    manager.versions[path].reviewed_at = (datetime.now() - timedelta(days=1)).isoformat()
    assert manager.check_outdated_documents() == [], \
        '阈值窗口内有近期人工评审的文档不应过期（不伪造 LastModified 也能重新计时）'
    assert manager.reviewed_fresh_documents() == [path], \
        '该文档应被识别为“靠人工评审保持新鲜”'
    assert manager.count_review_records() == 1

    # 人工评审不得触碰 LastModified / ContentHash
    assert str(manager.versions[path].last_modified)[:10] != datetime.now().strftime('%Y-%m-%d')
    assert manager.versions[path].content_hash == 'deadbeef'


def test_expired_review_makes_document_outdated_again(module):
    path = 'docs/en/configuration/rate-limiting.md'  # configuration 阈值 21 天
    manager = make_manager(module, SYNTHETIC_CONFIG, {path: 40})

    # 评审发生在 25 天前：比阈值 21 天还旧 -> 评审窗口已用完，应重新算过期
    manager.versions[path].reviewed_at = (datetime.now() - timedelta(days=25)).isoformat()
    assert manager.check_outdated_documents() == [path], \
        '评审本身超出阈值后必须重新算过期（“有 ReviewedAt 就永不过期”是错误实现）'
    assert manager.reviewed_fresh_documents() == []

    # 边界：评审 20 天前（< 阈值 21）-> 仍在窗口内
    manager.versions[path].reviewed_at = (datetime.now() - timedelta(days=20)).isoformat()
    assert manager.check_outdated_documents() == []

    # ReviewedAt 不可解析 -> 完全回退到 LastModified 的旧行为
    manager.versions[path].reviewed_at = 'not-a-date'
    assert manager.check_outdated_documents() == [path], \
        'ReviewedAt 不可解析时必须与引入该机制前行为一致'
    assert manager.reviewed_fresh_documents() == []


def test_reviewed_at_round_trip_in_dict(module):
    version = module.DocumentVersion(
        'docs/x.md', '1.0.0', '2026-01-01T00:00:00', 'hash', '2026-02-01T10:00:00'
    )
    dumped = version.to_dict()
    assert dumped['ReviewedAt'] == '2026-02-01T10:00:00', \
        f'to_dict 必须写出 ReviewedAt，否则下一次 --scan 会静默丢弃评审记录: {dumped}'

    restored = module.DocumentVersion.from_dict(dumped)
    assert restored.reviewed_at == '2026-02-01T10:00:00', 'from_dict 必须读回 ReviewedAt'

    # 旧数据（JSON 中没有 ReviewedAt 键）仍可解析，且视为“无评审记录”
    legacy = module.DocumentVersion.from_dict({
        'FilePath': 'docs/y.md', 'Version': '1.0.0',
        'LastModified': '2026-01-01T00:00:00', 'ContentHash': 'h',
    })
    assert legacy.reviewed_at == ''


def test_absent_or_unparseable_review_keeps_legacy_behaviour(module):
    """旧数据（无 ReviewedAt 键）的下线结果必须与引入评审机制前逐字一致"""
    now = datetime.now()
    legacy_versions = {
        path: {
            'FilePath': path,
            'Version': '1.0.0',
            'LastModified': (now - timedelta(days=days)).isoformat(),
            'ContentHash': 'deadbeef',
        }
        for path, days in TEMP_DOC_AGES.items()
    }
    manager = make_manager(module, SYNTHETIC_CONFIG, {})
    manager.versions = {
        path: module.DocumentVersion.from_dict(data) for path, data in legacy_versions.items()
    }

    expected = ['docs/en/configuration/rate-limiting.md', 'docs/en/monitoring/metrics.md']
    assert manager.check_outdated_documents() == expected, \
        f'缺少 ReviewedAt 的旧数据必须沿用原判定，实际 {manager.check_outdated_documents()}'
    assert manager.reviewed_fresh_documents() == []
    assert manager.count_review_records() == 0

    # ReviewedAt 存在但不可解析 -> 同样沿用原判定
    for version in manager.versions.values():
        version.reviewed_at = 'garbage'
    assert manager.check_outdated_documents() == expected, '不可解析的 ReviewedAt 不得影响判定'


def test_review_survives_real_scan(tmp_path):
    """回归测试：真实 --scan 之后 ReviewedAt 必须仍然存在（字段被丢弃的陷阱）"""
    versions_json = write_temp_project(tmp_path)
    reviewed_path = 'docs/en/configuration/already-reviewed.md'

    run_cli(tmp_path, ['--scan'])
    scanned = read_versions(versions_json)
    assert reviewed_path in scanned, f'--scan 应跟踪临时文档: {sorted(scanned)}'
    assert all('ReviewedAt' not in v for v in scanned.values()), \
        f'首次扫描不应凭空产生评审记录: {scanned}'

    stamp = (datetime.now() - timedelta(days=1)).replace(microsecond=0).isoformat()
    stdout = run_cli(tmp_path, ['--mark-reviewed', reviewed_path, '--reviewed-at', stamp]).stdout
    assert reviewed_path in stdout, f'--mark-reviewed 应逐条报告写入的路径: {stdout}'
    stamped = read_versions(versions_json)[reviewed_path]
    assert stamped['ReviewedAt'] == stamp, f'--mark-reviewed 未写入 ReviewedAt: {stamped}'

    # 真实 --scan（内容未变化）之后：ReviewedAt 仍在，且 LastModified/ContentHash 未被改动
    run_cli(tmp_path, ['--scan'])
    after = read_versions(versions_json)[reviewed_path]
    assert after['ReviewedAt'] == stamp, f'--scan 丢弃了评审记录: {after}'
    assert after['LastModified'] == stamped['LastModified'], '--scan 不得改写 LastModified'
    assert after['ContentHash'] == stamped['ContentHash'], '--scan 不得改写 ContentHash'


def test_mark_and_clear_reviewed_cli(tmp_path):
    versions_json = write_temp_project(tmp_path)
    run_cli(tmp_path, ['--scan'])

    target = 'docs/en/configuration/never-reviewed.md'
    other = 'docs/en/configuration/review-expired.md'

    run_cli(tmp_path, ['--mark-reviewed', target])
    stamped = read_versions(versions_json)[target]['ReviewedAt']
    assert stamped, '--mark-reviewed 应写入 ReviewedAt'
    assert datetime.fromisoformat(stamped).date() == datetime.now().date(), \
        f'未指定 --reviewed-at 时应写入当前时间: {stamped}'

    # 幂等：同一个评审时间重复标记 -> 值与现状相同，不改写文件
    run_cli(tmp_path, ['--mark-reviewed', target, '--reviewed-at', '2026-01-05'])
    assert read_versions(versions_json)[target]['ReviewedAt'] == '2026-01-05T00:00:00'
    before_bytes = versions_json.read_bytes()
    result = run_cli(tmp_path, ['--mark-reviewed', target, '--reviewed-at', '2026-01-05'])
    assert versions_json.read_bytes() == before_bytes, '重复标记同一时间不应改写文件'
    assert '幂等' in result.stdout, f'幂等路径应被明确报告: {result.stdout}'
    assert read_versions(versions_json)[target]['ReviewedAt'] == '2026-01-05T00:00:00'

    # 一次多个路径 + 显式 --reviewed-at（只写日期时补全为当天 00:00:00）
    run_cli(tmp_path, ['--mark-reviewed', target, other, '--reviewed-at', '2026-01-05'])
    versions = read_versions(versions_json)
    for path in (target, other):
        assert versions[path]['ReviewedAt'] == '2026-01-05T00:00:00', f'{path}: {versions[path]}'

    # 未跟踪路径：必须报错退出且不做任何修改（不得静默跳过）
    snapshot = versions_json.read_bytes()
    unknown = 'docs/en/configuration/not-tracked.md'
    result = run_cli(tmp_path, ['--mark-reviewed', unknown], expect_success=False)
    assert result.returncode != 0, f'未跟踪路径必须以非零退出码失败: {result.stdout}'
    assert '未被 docs/docs-versions.json 跟踪' in result.stdout, result.stdout
    assert unknown in result.stdout, '错误信息应指明具体路径'
    assert versions_json.read_bytes() == snapshot, '失败时不得部分写入'

    result = run_cli(tmp_path, ['--clear-reviewed', unknown], expect_success=False)
    assert result.returncode != 0, f'--clear-reviewed 同样必须对未跟踪路径失败: {result.stdout}'

    # 非法 --reviewed-at
    result = run_cli(tmp_path, ['--mark-reviewed', target, '--reviewed-at', '2026/01/05'],
                     expect_success=False)
    assert result.returncode != 0 and '--reviewed-at' in result.stdout, result.stdout

    # 清除评审记录
    run_cli(tmp_path, ['--clear-reviewed', target])
    assert 'ReviewedAt' not in read_versions(versions_json)[target], '--clear-reviewed 应移除评审字段'


def test_review_state_visible_in_console_and_report(tmp_path):
    """人工评审必须可见：控制台 + 报告 “## 过期文档” 一节（工作流按该标题抽取）"""
    versions_json = write_temp_project(tmp_path)
    run_cli(tmp_path, ['--scan'])

    now = datetime.now()
    data = json.loads(versions_json.read_text(encoding='utf-8'))
    for path in data['versions']:
        data['versions'][path]['LastModified'] = (now - timedelta(days=40)).isoformat()
    data['versions']['docs/en/configuration/already-reviewed.md']['ReviewedAt'] = \
        (now - timedelta(days=1)).isoformat()
    data['versions']['docs/en/configuration/review-expired.md']['ReviewedAt'] = \
        (now - timedelta(days=30)).isoformat()
    versions_json.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')

    result = run_cli(tmp_path, ['--check-outdated'])
    matches = re.findall(r'发现 [0-9]+ 个过期文档', result.stdout)
    assert matches == ['发现 2 个过期文档'], \
        f'工作流抓取的字面量必须只出现一次且数量正确: {result.stdout}'
    assert '人工评审已失效' in result.stdout, f'已失效的评审应被标注: {result.stdout}'
    assert '未记录人工评审' in result.stdout, f'无评审的文档应被标注: {result.stdout}'
    assert '靠人工评审保持新鲜' in result.stdout, f'靠评审保持新鲜的文档应单独列出: {result.stdout}'
    assert '已记录人工评审 (ReviewedAt): 2 个文档' in result.stdout, result.stdout

    report_path = tmp_path / 'docs-version-report.md'
    run_cli(tmp_path, ['--report', str(report_path)])
    report = report_path.read_text(encoding='utf-8')

    assert '## 过期文档' in report, '报告标题必须仍以 “## 过期文档” 开头（工作流正则依赖）'
    section = re.search(r'## 过期文档.*?(?=##|$)', report, re.S)
    assert section, f'工作流无法抽取过期文档小节:\n{report}'
    extracted = section.group(0)
    assert '人工评审已失效' in extracted, \
        f'评审失效状态必须落在被抽取的小节内（小节内不得出现 ### 截断）:\n{extracted}'
    assert 'docs/en/configuration/already-reviewed.md' in extracted, \
        f'靠人工评审保持新鲜的文档必须在被抽取的小节内可见:\n{extracted}'
    assert 'docs/en/configuration/never-reviewed.md' in extracted
    assert '人工评审' in extracted


def main():
    module = load_module()
    tests = [
        ('文档类型识别', lambda: test_document_type_resolution(module)),
        ('按类型阈值判定', lambda: test_per_type_thresholds(module)),
        ('ignore_patterns 生效', lambda: test_ignore_patterns(module)),
        ('无法解析日期视为过期且不崩溃', lambda: test_invalid_date_is_outdated_without_crash(module)),
        ('显式阈值仅覆盖默认阈值', lambda: test_explicit_threshold_only_overrides_default(module)),
        ('近期人工评审使文档不再过期', lambda: test_recent_review_keeps_document_in_window(module)),
        ('评审过期后重新判定为过期', lambda: test_expired_review_makes_document_outdated_again(module)),
        ('ReviewedAt 字典往返', lambda: test_reviewed_at_round_trip_in_dict(module)),
        ('无/坏 ReviewedAt 保持旧行为', lambda: test_absent_or_unparseable_review_keeps_legacy_behaviour(module)),
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
        tmp_path = Path(tmp)
        cli_tests = [
            ('CLI --check-outdated 三种形式', test_cli_forms),
            ('ReviewedAt 扛过一次真实 --scan', test_review_survives_real_scan),
            ('CLI --mark-reviewed / --clear-reviewed', test_mark_and_clear_reviewed_cli),
            ('评审状态在控制台与报告中可见', test_review_state_visible_in_console_and_report),
        ]

        for index, (name, func) in enumerate(cli_tests):
            case_root = tmp_path / f'case{index}'
            case_root.mkdir(parents=True, exist_ok=True)
            try:
                func(case_root)
                print(f'✅ {name}')
            except AssertionError as exc:
                failures.append((name, str(exc)))
                print(f'❌ {name}: {exc}')

    total = len(tests) + len(cli_tests)
    if failures:
        print(f'\n❌ {len(failures)}/{total} 个测试失败')
        return 1
    print(f'\n✅ {total}/{total} 个测试通过')
    return 0


if __name__ == '__main__':
    sys.exit(main())
