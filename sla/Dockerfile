# SLA 工具链镜像
#
# 目的：让「跑 SLA 工具」不再依赖目标环境装 Node。
# 客户机器只需要 Docker —— 有镜像就能跑，没有外网就 docker load 这个镜像的 tar 包。
#
# 构建：./build-image.sh        或  ./build-image.sh --save
# 运行：
#   docker run --rm --network host img-name preflight.mjs --base http://gw:8080 --user admin --pass '***'
#   docker run --rm --network host img-name diagnose/doctor.mjs --base-url http://gw:8080 --user admin --pass '***'
#   docker run --rm --network host img-name capacity/loadtest.mjs --url http://gw:8080/v1/chat/completions --concurrency 5 --duration 10
#
# 为什么用 alpine：50MB 左右，能塞进离线分发包；工具本身零第三方依赖，不需要 npm install。

FROM node:22-alpine

ARG SLA_VERSION=dev

LABEL org.opencontainers.image.title="jairouter-sla" \
      org.opencontainers.image.description="JAiRouter SLA toolchain (read-only): contract preflight, doctor, synthetic probes, load test" \
      org.opencontainers.image.vendor="JAiRouter" \
      jairouter.sla.contract.version="${SLA_VERSION}"

WORKDIR /sla
COPY . /sla/

# 工具全部只读、零依赖 ⇒ 不需要 root，也不需要任何写目录。
USER node

# 入口固定为 node，脚本名作为参数：镜像只提供运行环境，不替用户决定跑哪个工具。
ENTRYPOINT ["node"]
CMD ["--help"]
