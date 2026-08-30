# Knowledge Base Governance Standard Operating Procedure

<!-- Version Info -->
> **Document Version**: 1.0.0  
> **Last Updated**: 2026-05-21  
> **Git Commit**: -  
> **Author**: Lincoln
<!-- /Version Info -->


## 1. Overview

### What is Knowledge Base Governance

Knowledge base governance is a systematic process for inventorying, deduplicating, archiving, and version-tracking the documentation assets of the JAiRouter project (`docs/` public documentation with approximately 155 files, `innerdoc/` internal documentation with approximately 165 files).

### Why Governance is Needed

As the project iterates rapidly, the documentation base inevitably develops the following problems:

- **Stale documents**: Content that is out of sync with the current code/configuration, misleading developers
- **Duplicate documents**: The same content scattered across multiple paths, easily missed during updates
- **One-off artifacts**: Accumulated debugging records, temporary analyses, and other files that should not be retained long-term

### Governance Cadence

Knowledge base governance is executed **once per version release**, as a fixed step in the version release checklist.

---

## 2. Toolset

All governance scripts are located in the `scripts/knowledge-governance/` directory.

### kg-scanner.py — Inventory and Classification

Scans the `docs/` and `innerdoc/` directory trees and outputs a file inventory with classification statistics.

```bash
# Scan all directories and output a JSON report
python scripts/knowledge-governance/kg-scanner.py --root . --output innerdoc/governance/scan-report.json

# Scan innerdoc only
python scripts/knowledge-governance/kg-scanner.py --root . --scope innerdoc --output innerdoc/governance/scan-report.json
```

### kg-duplicate-detector.py — Duplicate Detection

Supports two detection modes: SHA-256 exact matching and filename similarity matching.

```bash
# Exact + similarity detection, output duplicate cluster report
python scripts/knowledge-governance/kg-duplicate-detector.py --root . --threshold 0.8 --output innerdoc/governance/duplicates.json
```

### kg-staleness-detector.py — Staleness Detection

Detects three types of staleness signals: index drift (index points to non-existent files), status conflicts (document claims vs. reality), and expired version literals.

```bash
# Detect stale documents
python scripts/knowledge-governance/kg-staleness-detector.py --root . --output innerdoc/governance/staleness.json
```

### kg-archive-mover.py — Archive Execution

Moves files marked for archiving to the corresponding subdirectory under `innerdoc/archive/` (move only, never delete).

```bash
# Generate archive plan only (dry-run)
python scripts/knowledge-governance/kg-archive-mover.py --plan innerdoc/governance/governance-proposal.md --dry-run

# Execute archiving
python scripts/knowledge-governance/kg-archive-mover.py --plan innerdoc/governance/governance-proposal.md --execute
```

### kg-version-tracker.py — Version Tracking

Manages the `innerdoc/docs-versions.json` version index file, supporting index regeneration and outdated document checks.

```bash
# Regenerate innerdoc index
python scripts/knowledge-governance/kg-version-tracker.py --regen-index --root .

# Check outdated documents (knowledge-base category, 30-day threshold)
python scripts/knowledge-governance/kg-version-tracker.py --check-outdated --category knowledge-base --threshold 30

# Check outdated documents across all categories
python scripts/knowledge-governance/kg-version-tracker.py --check-outdated --all
```

---

## 3. Governance Workflow

Knowledge base governance follows a five-step workflow:

### Step 1: Scan

Run the three scanners to generate raw reports:

```bash
python scripts/knowledge-governance/kg-scanner.py --root . --output innerdoc/governance/scan-report.json
python scripts/knowledge-governance/kg-duplicate-detector.py --root . --threshold 0.8 --output innerdoc/governance/duplicates.json
python scripts/knowledge-governance/kg-staleness-detector.py --root . --output innerdoc/governance/staleness.json
```

Reports are output to the `innerdoc/governance/` directory.

### Step 2: Analyze

A mimo sub-agent reads the three reports, combines them with duplicate cluster information, and generates a structured governance proposal `innerdoc/governance/governance-proposal.md` containing four sections:

- **Merge Map**: Which duplicate files should be merged into a single authoritative version
- **Archive List**: One-off artifacts and stale files that should be archived
- **Content Updates**: Files whose version literals need updating or status claims need correction
- **Index Plan**: Additions, deletions, and modifications needed in index files

### Step 3: Manual Approval

The developer reviews `governance-proposal.md`, confirms or modifies the proposal content, and then approves execution.

> **Important**: Archiving and merging operations must not be executed without manual approval.

### Step 4: Execute

Execute according to the approved proposal:

```bash
# 1. Archive files
python scripts/knowledge-governance/kg-archive-mover.py --plan innerdoc/governance/governance-proposal.md --execute

# 2. Manually complete knowledge-base merges (operate according to Merge Map)

# 3. Regenerate indexes
python scripts/knowledge-governance/kg-version-tracker.py --regen-index --root .
```

### Step 5: Update

- Record the governance version entry in `innerdoc/开发计划2026/`
- Update the task tracking table, marking the governance task as complete

---

## 4. Archiving Strategy

### Archiving Criteria

Files meeting any of the following conditions may be archived:

| Criteria | Description |
|----------|-------------|
| Byte-identical duplicates | Files with identical SHA-256 hashes (one authoritative copy is retained) |
| One-off debug artifacts | Files whose names contain heuristic keywords such as `debug`, `temp`, `tmp`, `test-output`, `scratch` |
| Content subsumed by knowledge-base | File's core content has been integrated into the corresponding `knowledge-base/` document |

### Files That Must NEVER Be Archived

- `knowledge-base/*` — Core knowledge base documents
- `16-版本发布/*` — Version release documents
- `开发计划/`, `任务跟踪表` — Actively used project management documents

### Archive Directory Structure

Archived files are moved to the `innerdoc/archive/<original category>/` directory, preserving the original filename. Archiving is a **move operation**, not a delete operation, ensuring traceability and recoverability.

---

## 5. Duplicate Detection

### Exact Duplicate Detection (SHA-256)

SHA-256 hashes are computed for all files. Files with identical hashes are grouped into a duplicate cluster. Handling:

- The copy with the shortest path or most recent modification is retained as the authoritative version
- All other copies are archived

### Near-Duplicate Detection (Filename Similarity)

Normalized filenames are compared using a similarity threshold of **0.8** (80%):

1. Remove file extensions, date prefixes, and version suffixes from filenames
2. Convert to lowercase and normalize whitespace
3. Calculate edit distance similarity
4. File pairs with similarity > 0.8 are flagged as near-duplicates

Near-duplicates require manual confirmation of whether they are truly duplicates — they are not automatically archived.

---

## 6. Version Tracking

### innerdoc/docs-versions.json

The structure of `innerdoc/docs-versions.json` matches `docs/docs-versions.json`:

```json
{
  "versions": {
    "innerdoc/knowledge-base/xxx.md": {
      "FilePath": "innerdoc/knowledge-base/xxx.md",
      "Version": "1.0.0",
      "LastModified": "2026-05-21T00:00:00",
      "ContentHash": "abc123...",
      "GitCommit": "xxxxxxx",
      "Author": "Lincoln",
      "ChangeSummary": "",
      "Dependencies": []
    }
  }
}
```

### Outdated Check Strategy

Outdated thresholds are configured via `docs/docs-version-config.yml` and checked by category using `kg-version-tracker.py --check-outdated`:

| Category | Threshold | Description |
|----------|-----------|-------------|
| knowledge-base | 30 days | Knowledge base documents should be kept up to date |
| development | 90 days | Development documents allow longer update cycles |
| releases | Never | Version release documents are historical records |

---

## 7. Index Maintenance

After governance execution, the following three index files must be regenerated:

| Index File | Description |
|------------|-------------|
| `innerdoc/README-INNERDOC.md` | Top-level README for the innerdoc directory, containing category navigation |
| `innerdoc/INDEX.json` | JSON-format index for innerdoc, consumed by tooling |
| `innerdoc/00-索引/README.md` | Category index page |

Regeneration command:

```bash
python scripts/knowledge-governance/kg-version-tracker.py --regen-index --root .
```

This command scans the innerdoc directory structure and automatically updates the three index files listed above.

---

## 8. CI Integration

### CI Automation (docs/ Directory)

The following checks are executed automatically by GitHub Actions:

| Check | Workflow | Description |
|-------|----------|-------------|
| Navigation file validation | `scripts/docs/validate-nav-files.py` | Ensures all files referenced in mkdocs.yml exist |
| Document staleness detection | `docs-version-management.yml` | Daily check for docs not updated in 30+ days, automatically creates a GitHub Issue |

### Local Governance (innerdoc/ Directory)

innerdoc governance is a **local operation**, not included in CI:

1. Manually run scanners to generate reports
2. Sub-agent generates governance proposal
3. After manual approval, execute archiving and index updates locally

> **Note**: innerdoc staleness is not checked in CI because internal documentation follows a different update cadence than public documentation.

---

## 9. Version Release Checklist

Each version release should include the following item in its release checklist:

```markdown
- [ ] Knowledge base governance: Run the knowledge-governance skill (scan → governance proposal → approval → execution → index update)
```

Suggested execution order for the complete version release checklist:

1. Code freeze and feature verification
2. Documentation update and synchronization check
3. **Knowledge base governance** (scan → proposal → approval → execution → index update)
4. Version number update and tagging
5. Release build and deployment

---

## 10. Troubleshooting

### Stale Index After Manual Edits

**Symptom**: After manually adding or deleting innerdoc files, index files are out of sync.

**Solution**:

```bash
# Regenerate all indexes
python scripts/knowledge-governance/kg-version-tracker.py --regen-index --root .
```

### Archive Misclassification (Heuristic False Positive)

**Symptom**: A normal file is incorrectly flagged as a one-off artifact by the filename heuristic (e.g., contains `test`) and archived.

**Solution**:

1. Move the file back from `innerdoc/archive/<original category>/` to its original location
2. Manually exclude the file from the Archive List in the governance proposal
3. Consider adding the filename pattern to the heuristic exclusion list

### Scanner Fails on Encoding Issues

**Symptom**: Scanner exits with an error due to file encoding (non-UTF-8).

**Solution**:

```bash
# Retry specifying the encoding
python scripts/knowledge-governance/kg-scanner.py --root . --encoding gbk --output innerdoc/governance/scan-report.json

# Or exclude problematic files
python scripts/knowledge-governance/kg-scanner.py --root . --exclude-pattern "*.bak" --output innerdoc/governance/scan-report.json
```
