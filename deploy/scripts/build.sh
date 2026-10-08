#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

echo "==> 构建前端"
npm --prefix frontend ci
npm --prefix frontend run build

echo "==> 构建后端"
mvn -f backend/pom.xml clean package -DskipTests

echo "==> 安装支付侧车依赖"
npm --prefix payments ci

echo "==> 产物"
ls -lh backend/target/shuati-backend.jar
du -sh frontend/dist
du -sh payments/node_modules
