#!/usr/bin/env bash
# 사용법: deploy.sh <ECR 이미지>. 같은 디렉터리의 .env를 컨테이너에 주입한다.
set -euo pipefail

IMAGE="${1:?이미지를 지정해 주세요}"
ENV_FILE="$(cd "$(dirname "$0")" && pwd)/.env"
UPSTREAM_FILE="${UPSTREAM_FILE:-/etc/nginx/botem-upstream.inc}"
HEALTH_RETRIES="${HEALTH_RETRIES:-60}"
DRAIN_SECONDS="${DRAIN_SECONDS:-10}"
BLUE_PORT=8081
GREEN_PORT=8082

if grep -q "127.0.0.1:$BLUE_PORT" "$UPSTREAM_FILE"; then
    ACTIVE=botem-blue NEXT=botem-green NEXT_PORT=$GREEN_PORT
else
    ACTIVE=botem-green NEXT=botem-blue NEXT_PORT=$BLUE_PORT
fi

abort() {
    echo "배포 실패: $1. $ACTIVE 유지" >&2
    docker logs --tail 100 "$NEXT" >&2 || true
    docker rm -f "$NEXT" >/dev/null 2>&1 || true
    exit 1
}

REGISTRY="${IMAGE%%/*}"
REGION="$(echo "$REGISTRY" | cut -d. -f4)"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"
docker pull "$IMAGE"

docker rm -f "$NEXT" >/dev/null 2>&1 || true
docker run -d --name "$NEXT" --restart unless-stopped --env-file "$ENV_FILE" \
    -p "127.0.0.1:$NEXT_PORT:8080" "$IMAGE"

healthy=false
for _ in $(seq "$HEALTH_RETRIES"); do
    if curl -fsS "http://127.0.0.1:$NEXT_PORT/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
        healthy=true
        break
    fi
    sleep 2
done
[ "$healthy" = true ] || abort "헬스체크 미통과"

PREVIOUS_UPSTREAM="$(cat "$UPSTREAM_FILE")"
echo "server 127.0.0.1:$NEXT_PORT;" | sudo tee "$UPSTREAM_FILE" >/dev/null
if ! sudo nginx -t || ! sudo nginx -s reload; then
    echo "$PREVIOUS_UPSTREAM" | sudo tee "$UPSTREAM_FILE" >/dev/null
    abort "Nginx 전환 실패"
fi

# 이전 Nginx 워커가 기존 연결을 마칠 시간을 준 뒤, Spring graceful shutdown(30초)보다 길게 기다린다
sleep "$DRAIN_SECONDS"
docker stop -t 35 "$ACTIVE" >/dev/null 2>&1 || true
docker rm "$ACTIVE" >/dev/null 2>&1 || true
docker image prune -af >/dev/null

echo "배포 완료: $NEXT ($IMAGE)"
