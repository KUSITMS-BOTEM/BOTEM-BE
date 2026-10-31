#!/usr/bin/env bash
# deploy.sh 전환 분기 검증. docker, nginx 등은 스텁으로 대체한다.
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir "$TMP/bin"
stub() { printf '#!/usr/bin/env bash\n%s\n' "$2" > "$TMP/bin/$1"; chmod +x "$TMP/bin/$1"; }
stub aws 'echo token'
stub docker 'echo "docker $*" >> "$TMP_LOG"; cat >/dev/null <&- 2>/dev/null; exit 0'
stub curl '[ "$HEALTH" = up ] && echo "{\"status\":\"UP\"}" || exit 22'
stub sudo '"$@"'
stub nginx '[ "$NGINX" = ok ]'
stub sleep 'exit 0'

fail=0
check() { if eval "$2"; then echo "ok: $1"; else echo "FAIL: $1"; fail=1; fi; }

run() { # 초기 포트, 헬스 결과, nginx 결과
    export UPSTREAM_FILE="$TMP/upstream" TMP_LOG="$TMP/log" HEALTH="$2" NGINX="$3" HEALTH_RETRIES=2
    echo "server 127.0.0.1:$1;" > "$UPSTREAM_FILE"
    : > "$TMP_LOG"
    PATH="$TMP/bin:$PATH" bash "$DIR/deploy.sh" 123.dkr.ecr.ap-northeast-2.amazonaws.com/botem:sha >/dev/null 2>&1
    STATUS=$?
}

run 8081 up ok
check "blue에서 green으로 전환" '[ $STATUS = 0 ] && grep -q 8082 "$UPSTREAM_FILE"'
check "green을 8082로 실행" 'grep -q "run .*--name botem-green .*127.0.0.1:8082:8080" "$TMP_LOG"'
check "이전 blue 종료" 'grep -q "stop -t 35 botem-blue" "$TMP_LOG"'

run 8082 up ok
check "green에서 blue로 전환" '[ $STATUS = 0 ] && grep -q 8081 "$UPSTREAM_FILE"'

run 8081 down ok
check "헬스 실패 시 전환 안 함" '[ $STATUS != 0 ] && grep -q 8081 "$UPSTREAM_FILE"'
check "헬스 실패 시 blue 유지" '! grep -q "stop .*botem-blue" "$TMP_LOG"'
check "헬스 실패 시 green 제거" '[ "$(grep -c "rm -f botem-green" "$TMP_LOG")" = 2 ]'

run 8081 up fail
check "Nginx 실패 시 upstream 복구" '[ $STATUS != 0 ] && grep -q 8081 "$UPSTREAM_FILE"'
check "Nginx 실패 시 blue 유지" '! grep -q "stop .*botem-blue" "$TMP_LOG"'

exit $fail
