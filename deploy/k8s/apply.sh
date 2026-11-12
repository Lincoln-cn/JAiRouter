#!/usr/bin/env bash
#
# JAiRouter Kubernetes 部署入口 —— 保证「先迁移、后滚动」(issue #1111)。
#
# 为什么需要这层：kubectl apply -k 会把清单里所有对象一次性提交，Deployment 的 Pod 可能在
# 迁移 Job 还没跑完时就起来，各副本自己去跑 Flyway。PG 上有锁能把它们串行化，但
# `ddl-auto: validate` 会在迁移未完成时失败 → 一轮 CrashLoopBackOff；H2 上更是并发 DDL。
# 所以迁移 Job 被移出 base，由本脚本按「apply → wait → apply」的顺序串起来。
#
# 用法：
#   deploy/k8s/apply.sh                 # 迁移 + 部署
#   deploy/k8s/apply.sh --skip-migrate  # 只部署（已单独跑过迁移时）
#   deploy/k8s/apply.sh --overlay prod  # 部署 overlays/prod（默认 base）
#
# 前置：kubectl 已指向目标集群；namespace 由各 kustomization 自带（base=migrate=jairouter，
# overlays/prod=jairouter，overlays/dev=jairouter-dev）。
# 密钥：JWT_SECRET / INITIAL_ADMIN_PASSWORD 所在的 Secret 需事先创建，
# 步骤见 docs/zh/deployment/kubernetes.md。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NAMESPACE="jairouter"
JOB_NAME="jairouter-schema-init"
OVERLAY="base"
SKIP_MIGRATE=0
WAIT_SECONDS=600

while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-migrate)
            SKIP_MIGRATE=1
            shift
            ;;
        --overlay)
            OVERLAY="${2:-}"
            shift 2
            ;;
        --namespace)
            NAMESPACE="${2:-}"
            shift 2
            ;;
        --timeout)
            WAIT_SECONDS="${2:-}"
            shift 2
            ;;
        -h|--help)
            sed -n '3,26p' "${BASH_SOURCE[0]}"
            exit 0
            ;;
        *)
            echo "未知参数: $1（--help 看用法）" >&2
            exit 2
            ;;
    esac
done

if ! command -v kubectl >/dev/null 2>&1; then
    echo "找不到 kubectl" >&2
    exit 1
fi

if [[ "$OVERLAY" == "base" ]]; then
    TARGET_DIR="${SCRIPT_DIR}/base"
else
    TARGET_DIR="${SCRIPT_DIR}/overlays/${OVERLAY}"
    if [[ ! -d "$TARGET_DIR" ]]; then
        echo "overlay 不存在: $TARGET_DIR" >&2
        exit 1
    fi
fi

if [[ "$SKIP_MIGRATE" -eq 0 ]]; then
    echo "==> 1/3 应用迁移 Job（migrate profile，跑完即退出）"
    # 上一次的 Job 可能还留着（ttlSecondsAfterFinished 600s 内）：Job 的 template 不可变，
    # 直接重复 apply 会报字段不可变，先删掉再建。
    kubectl delete job "$JOB_NAME" -n "$NAMESPACE" --ignore-not-found >/dev/null
    kubectl apply -k "${SCRIPT_DIR}/migrate"

    echo "==> 2/3 等待迁移完成（最多 ${WAIT_SECONDS}s）"
    if ! kubectl wait --for=condition=complete --timeout="${WAIT_SECONDS}s" -n "$NAMESPACE" "job/${JOB_NAME}"; then
        echo "" >&2
        echo "迁移未在 ${WAIT_SECONDS}s 内完成，已中止部署（不要跳过这一步直接扩副本）。" >&2
        echo "排查：" >&2
        echo "  kubectl logs -n ${NAMESPACE} job/${JOB_NAME} --tail=200" >&2
        echo "  kubectl describe job -n ${NAMESPACE} ${JOB_NAME}" >&2
        exit 1
    fi
else
    echo "==> 跳过迁移（--skip-migrate）"
fi

echo "==> 3/3 应用 ${OVERLAY}"
kubectl apply -k "$TARGET_DIR"

echo "==> 等待滚动完成"
if ! kubectl rollout status deployment/jairouter -n "$NAMESPACE" --timeout="${WAIT_SECONDS}s"; then
    echo "滚动未在 ${WAIT_SECONDS}s 内完成，看：" >&2
    echo "  kubectl get pods -n ${NAMESPACE}" >&2
    echo "  kubectl logs -n ${NAMESPACE} deploy/jairouter --tail=200" >&2
    exit 1
fi

echo "==> 完成。健康检查：kubectl port-forward -n ${NAMESPACE} svc/jairouter 8080:8080，再访问 /actuator/health"
