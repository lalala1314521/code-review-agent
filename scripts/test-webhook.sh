#!/bin/bash
# 本地测试 GitLab webhook 接入
#
# 用法：
#   1. 先启动服务：mvn spring-boot:run
#   2. 在另一个终端跑：./scripts/test-webhook.sh
#
# 期望：服务日志看到 "webhook received ... review completed"
#       返回 "review completed"

# webhook secret，要和 application-dev.yml 里的 gitlab.webhook-secret 一致
TOKEN="your-webhook-secret-here"

# 服务地址
HOST="http://localhost:8080"

echo ">>> 发送 webhook 到 $HOST/webhook/gitlab"
echo ">>> X-Gitlab-Token: $TOKEN"
echo

curl -sS -X POST "$HOST/webhook/gitlab" \
  -H "X-Gitlab-Token: $TOKEN" \
  -H "Content-Type: application/json" \
  -d @scripts/test-webhook-payload.json \
  -w "\n\nHTTP status: %{http_code}\n"

echo
echo ">>> 健康检查："
curl -sS "$HOST/api/health" | head
echo
