#!/usr/bin/env bash
# 构建 SLA 工具链镜像；加 --save 时同时导出离线分发包。
#
#   ./build-image.sh            # 只构建
#   ./build-image.sh --save     # 构建 + docker save 成 tar.gz
#
# 版本号取自 contract.json 的 toolingVersion，保证镜像 tag 与契约版本一致 ——
# 客户环境里 `docker images` 一眼能看出这个镜像是配哪版网关契约的。
set -euo pipefail
cd "$(dirname "$0")"

VERSION=$(sed -n 's/.*"toolingVersion"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' contract.json | head -1)
if [ -z "${VERSION}" ]; then
  echo "无法从 contract.json 读取 toolingVersion" >&2
  exit 1
fi

IMAGE="${SLA_IMAGE:-sodlinken/jairouter-sla:${VERSION}}"

echo "==> 构建 ${IMAGE}（契约版本 ${VERSION}）"
docker build --build-arg SLA_VERSION="${VERSION}" -t "${IMAGE}" .

echo
echo "==> 自检：容器内 node 与工具文件"
docker run --rm "${IMAGE}" --version
docker run --rm "${IMAGE}" -e "console.log(require('fs').readdirSync('/sla').join(' '))"

if [ "${1:-}" = "--save" ]; then
  OUT="jairouter-sla-${VERSION}.tar.gz"
  echo
  echo "==> 导出 ${OUT}"
  docker save "${IMAGE}" | gzip -9 > "${OUT}"
  echo "    大小：$(du -h "${OUT}" | cut -f1)"
  echo
  echo "离线环境导入："
  echo "    docker load < ${OUT}"
  echo "    docker run --rm --network host ${IMAGE} preflight.mjs --base http://gw:8080 --user admin --pass '***'"
fi

echo
echo "完成：${IMAGE}"
