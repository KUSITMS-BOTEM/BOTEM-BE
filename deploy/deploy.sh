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

# 요청 횟수 제한·캐시용 Redis (#282). 앱 컨테이너와 같은 내부 네트워크에만 열고 호스트 포트는 열지 않는다.
# 캐시·카운터만 담아 디스크에 저장하지 않고, 데이터가 48MB를 넘으면 오래 안 쓴 키부터 지운다.
# Redis 자체 한도는 데이터 기준이라, 프로세스 전체는 컨테이너 메모리 상한(96MB)으로 막는다. 배포마다 다시 만들지 않는다.
# .env에 REDIS_HOST가 있으면(ElastiCache 등) 이 컨테이너를 쓰지 않는다
REDIS_MEMORY=96m
docker network inspect botem >/dev/null 2>&1 || docker network create botem >/dev/null
if grep -q '^REDIS_HOST=' "$ENV_FILE"; then
    REDIS_ARGS=()
else
    REDIS_ARGS=(-e REDIS_HOST=botem-redis)
    if [ -z "$(docker ps -q -f name=^botem-redis$)" ]; then
        docker rm -f botem-redis >/dev/null 2>&1 || true
        docker run -d --name botem-redis --network botem --restart unless-stopped \
            --memory "$REDIS_MEMORY" --memory-swap "$REDIS_MEMORY" \
            --log-opt max-size=10m --log-opt max-file=3 \
            redis:7.4-alpine redis-server --save "" --appendonly no --maxmemory 48mb --maxmemory-policy allkeys-lru
    else
        # 상한 없이 먼저 만든 컨테이너에도 같은 상한을 건다
        docker update --memory "$REDIS_MEMORY" --memory-swap "$REDIS_MEMORY" botem-redis >/dev/null
    fi
fi

docker rm -f "$NEXT" >/dev/null 2>&1 || true
# 컨테이너 로그는 Alloy가 Grafana Cloud(Loki)로 보내므로, 서버에는 최근 30MB만 남겨 디스크가 차지 않게 한다
docker run -d --name "$NEXT" --restart unless-stopped --env-file "$ENV_FILE" \
    --network botem ${REDIS_ARGS[@]+"${REDIS_ARGS[@]}"} \
    --log-opt max-size=10m --log-opt max-file=3 \
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
