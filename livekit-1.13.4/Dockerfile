# Copyright 2023 LiveKit, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# ============================================================
# 国内镜像加速配置
# ------------------------------------------------------------
# 通过 ARG 指定基础镜像的仓库前缀，默认使用 DaoCloud 公共镜像
# （已在本地 Docker daemon 的 registry-mirrors 中验证可用）。
# 若需切换镜像源，构建时传入 --build-arg BASE_REGISTRY=xxx 即可。
#
# 可选镜像源（均需带 /library 后缀）：
#   docker.m.daocloud.io/library       (DaoCloud，默认)
#   hub-mirror.c.163.com/library       (网易)
#   docker.nju.edu.cn/library          (南京大学)
#   mirror.ccs.tencentyun.com/library  (腾讯云)
#   docker.1ms.run/library             (1ms.run)
#
# 更推荐的做法（无需改动本文件）：
#   在 Docker 守护进程配置（如 /etc/docker/daemon.json 或
#   Docker Desktop 的 Registry mirrors）中加入：
#     "registry-mirrors": ["https://docker.m.daocloud.io"]
#   然后直接用 FROM golang:1.26-alpine 拉取即可自动走加速。
# ============================================================
ARG BASE_REGISTRY=docker.m.daocloud.io/library

FROM ${BASE_REGISTRY}/golang:1.26-alpine AS builder

ARG TARGETPLATFORM
ARG TARGETARCH
RUN echo building for "$TARGETPLATFORM"

# 使用国内 Go 模块代理（goproxy.cn）。
# GOSUMDB=off 用于规避国内访问官方 sumdb 不稳定问题；
# 若需保留校验，可改为 GOSUMDB=sum.golang.google.cn。
ENV GOPROXY=https://goproxy.cn,direct \
    GOSUMDB=off \
    GO111MODULE=on

WORKDIR /workspace

# Copy the Go Modules manifests
COPY go.mod go.mod
COPY go.sum go.sum
# cache deps before building and copying source so that we don't need to re-download as much
# and so that source changes don't invalidate our downloaded layer
RUN go mod download

# Copy the go source
COPY cmd/ cmd/
COPY pkg/ pkg/
COPY test/ test/
COPY tools/ tools/
COPY version/ version/

RUN CGO_ENABLED=0 GOOS=linux GOARCH=$TARGETARCH go build -a -o livekit-server ./cmd/server

FROM ${BASE_REGISTRY}/alpine

COPY --from=builder /workspace/livekit-server /livekit-server

# Run the binary.
ENTRYPOINT ["/livekit-server"]
