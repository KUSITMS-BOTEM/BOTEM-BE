# 배포

`develop`에 병합되면 `.github/workflows/deploy.yml`이 이미지를 ECR에 push하고, EC2에서 `deploy.sh`로 Blue(8081)/Green(8082)을 전환합니다.

## EC2 최초 설정 (1회)

Docker, Nginx, AWS CLI를 설치하고 배포 사용자가 `docker`와 비밀번호 없는 `sudo`를 쓸 수 있게 합니다. EC2에는 ECR 읽기 권한이 있는 IAM Role을 연결합니다.

워크플로는 `nginx.conf`를 전송하지 않으므로 로컬에서 먼저 올립니다.

```bash
scp -i <키.pem> deploy/nginx.conf <EC2 사용자>@<EC2 주소>:~/
```

EC2에서 실행합니다.

```bash
echo 'server 127.0.0.1:8081;' | sudo tee /etc/nginx/botem-upstream.inc
sudo mv ~/nginx.conf /etc/nginx/conf.d/botem.conf
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

이어서 HTTPS 인증서를 발급합니다. `api.botem.me`의 DNS가 EC2를 가리키고 80/443이 열려 있어야 합니다.

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.botem.me -m <이메일> --agree-tos --redirect -n
```

위 절차는 새 서버에서만 실행합니다. 운영 중인 서버에서 다시 실행하면 Certbot HTTPS 설정과 활성 upstream이 덮어써집니다. `deploy/nginx.conf`는 배포 시 자동 반영되지 않으므로, 운영 설정을 바꿀 때는 `/etc/nginx/conf.d/botem.conf`를 직접 수정하고 `sudo nginx -t && sudo systemctl reload nginx`를 실행합니다.

운영 서버에 도메인을 추가할 때는 `botem.conf`의 `server_name`(80, 443 블록 모두)에 새 도메인을 추가하고 reload한 뒤, 기존 인증서를 확장합니다. 인증서 이름은 최초 발급 도메인(`api.botem.site`)으로 유지됩니다.

```bash
sudo certbot --nginx -d api.botem.site -d api.botem.me --expand
```

## 보안 그룹

- EC2: 80/443만 공개하고 22는 열지 않습니다. 배포 중에만 워크플로가 러너 IP를 허용합니다.
- RDS: EC2 보안 그룹에서 오는 3306만 허용합니다.

## GitHub Actions AWS 인증 (OIDC)

워크플로는 장기 액세스 키 없이 GitHub OIDC 토큰으로 IAM Role을 맡아 실행마다 임시 자격 증명을 받습니다.

1. IAM → ID 제공업체 → 공급자 추가: OpenID Connect, URL `https://token.actions.githubusercontent.com`, 대상 `sts.amazonaws.com`
2. IAM 정책을 만들고 다음 권한만 줍니다.
   - `ecr:GetAuthorizationToken` (리소스 `*`)
   - 대상 저장소의 ECR push (`BatchCheckLayerAvailability`, `InitiateLayerUpload`, `UploadLayerPart`, `CompleteLayerUpload`, `PutImage`, `BatchGetImage`)
   - 대상 보안 그룹의 `ec2:AuthorizeSecurityGroupIngress`, `ec2:RevokeSecurityGroupIngress`
3. IAM → 역할 → 웹 자격 증명으로 Role을 만들고 위 정책을 연결합니다. 신뢰 정책은 `develop` 브랜치만 허용합니다.

```json
"Condition": {
  "StringEquals": {
    "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
    "token.actions.githubusercontent.com:sub": "repo:KUSITMS-BOTEM/BOTEM-BE:ref:refs/heads/develop"
  }
}
```

4. Role ARN을 GitHub Secret `AWS_ROLE_ARN`에 등록합니다.

다른 브랜치에서 `workflow_dispatch`로 실행하면 신뢰 정책에 막혀 AWS 인증 단계에서 실패합니다.

## GitHub Secrets

| 구분 | 이름 |
|---|---|
| AWS | `AWS_ROLE_ARN`, `AWS_REGION`, `ECR_REPOSITORY`, `EC2_SECURITY_GROUP_ID` |
| SSH | `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `EC2_KNOWN_HOSTS` (`ssh-keyscan <host>` 결과를 확인 후 등록) |
| 앱 | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI`, `KAKAO_ALLOWED_REDIRECT_URIS` (쉼표로 구분, 카카오 콘솔 Redirect URI와 일치), `JWT_SECRET`, `PAYOUT_ACCOUNT_ENCRYPTION_KEY`, `AUTH_COOKIE_DOMAIN` (`.botem.me`), `SENTRY_DSN` (Sentry `botem-be` 프로젝트 DSN) |
| 공개 URL | `WISH_PROFILE_BASE_URL` (`https://botem.me`), `FUNDING_SHARE_BASE_URL` (`https://botem.me/share`), `CONTRIBUTION_THANK_CARD_BASE_URL` (`https://botem.me/thank-cards`) |

`PAYOUT_ACCOUNT_ENCRYPTION_KEY`는 16, 24 또는 32바이트 난수를 Base64로 인코딩해 등록합니다. 운영 중 키를 잃거나 임의로 교체하면 저장된 계좌번호를 복호화할 수 없습니다.

## 스키마 변경

전환 중에는 이전 버전이 새 스키마로 동작합니다. 컬럼 삭제나 이름 변경은 컬럼 추가 → 코드 전환 → 다음 배포에서 삭제 순서로 나눕니다.

## 롤백

워크플로를 이전 커밋에서 다시 실행하거나, EC2에서 이전 이미지 태그로 실행합니다. Flyway로 바뀐 스키마는 되돌아가지 않습니다.

```bash
bash ~/botem/deploy.sh <ECR 이미지>:<이전 커밋 SHA>
```

## 모니터링

EC2에는 수집 에이전트(Grafana Alloy)만 두고, 지표는 Grafana Cloud(무료 플랜: 지표 1만 개, 14일 보관)로 보냅니다. t3.micro는 메모리·디스크 여유가 작고, 감시 대상과 같은 서버에 모니터링을 두면 서버 장애 때 알림도 함께 끊기기 때문입니다.

```
Spring Boot(8081/8082) /actuator/prometheus ─┐
서버 CPU·메모리·스왑·디스크 ─────────────────┤ Alloy ── remote write ──> Grafana Cloud (저장·대시보드·Discord 알림)
```

`/actuator/prometheus`는 인증 없이 열려 있지만 컨테이너 포트가 `127.0.0.1`에만 바인딩되고 Nginx가 `/actuator`를 외부에서 404로 막으므로 서버 안에서만 접근할 수 있습니다. 운영 Nginx 설정을 바꾼 뒤에는 `curl -i https://api.botem.me/actuator/prometheus`가 404인지 확인합니다.

### Alloy 설치 (1회)

1. Grafana Cloud에서 스택을 만들고 **Prometheus 원격 쓰기 주소, 사용자 번호, `metrics:write` 권한 토큰**을 발급받습니다.
2. 수집 설정을 서버로 올립니다.

```bash
scp -i <키.pem> deploy/monitoring/config.alloy <EC2 사용자>@<EC2 주소>:~/
```

3. EC2에서 설치하고 접속 정보를 환경 변수로 넣습니다.

```bash
sudo mkdir -p /etc/apt/keyrings
wget -q -O - https://apt.grafana.com/gpg.key | gpg --dearmor | sudo tee /etc/apt/keyrings/grafana.gpg > /dev/null
echo "deb [signed-by=/etc/apt/keyrings/grafana.gpg] https://apt.grafana.com stable main" | sudo tee /etc/apt/sources.list.d/grafana.list
sudo apt-get update && sudo apt-get install -y alloy

sudo mv ~/config.alloy /etc/alloy/config.alloy
sudo tee -a /etc/default/alloy > /dev/null <<'ENV'
GRAFANA_CLOUD_PROMETHEUS_URL=<원격 쓰기 주소>
GRAFANA_CLOUD_PROMETHEUS_USER=<사용자 번호>
GRAFANA_CLOUD_API_TOKEN=<토큰>
ENV
sudo chmod 600 /etc/default/alloy
sudo systemctl enable --now alloy
sudo systemctl status alloy --no-pager
```

수집 설정을 바꾸면 `/etc/alloy/config.alloy`를 교체하고 `sudo systemctl reload alloy`를 실행합니다.

### 대시보드

Grafana Cloud에서 아래 공개 대시보드를 가져온 뒤 필요한 패널만 남깁니다.

- Spring Boot·JVM: 대시보드 ID `19004` (HTTP 요청, HikariCP, JVM 메모리·GC)
- 서버: 대시보드 ID `1860` (Node Exporter Full)

### 알림 규칙

Grafana Cloud의 `BOTEM` 폴더에 아래 규칙(평가 그룹 `botem`, 1분 간격)이 등록돼 있고, 알림 경로의 기본 연락처는 Discord(`botem-discord`)입니다. 규칙은 '쿼리 값'과 '조건'으로 나뉘어 있습니다. Blue/Green 중 대기 중인 쪽은 항상 `up=0`이므로 앱 수집 중단은 두 대상 중 최댓값으로 판단합니다. 서버 지표의 `job` 라벨은 Alloy의 서버 수집기가 붙이는 `integrations/unix`입니다.

| 알림 | 쿼리 값 | 조건 | 지속 | 데이터 없음 |
|---|---|---|---|---|
| 디스크 사용률 85% 초과 | `1 - node_filesystem_avail_bytes{mountpoint="/"} / node_filesystem_size_bytes{mountpoint="/"}` | > 0.85 | 5분 | 정상 |
| API 5xx 비율 5% 초과 | `(sum(rate(http_server_requests_seconds_count{application="botem",status=~"5.."}[5m])) or vector(0)) / sum(rate(http_server_requests_seconds_count{application="botem"}[5m]))` | > 0.05 | 5분 | 정상 |
| API 응답 p95 1초 초과 | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application="botem"}[5m])))` | > 1 | 10분 | 정상 |
| 앱 수집 중단 | `max(up{job="botem"})` | < 1 | 3분 | 알림 |
| 서버 수집 중단 | `max(up{job="integrations/unix"})` | < 1 | 3분 | 알림 |
| DB 커넥션 대기 발생 | `max(hikaricp_connections_pending{application="botem"})` | > 0 | 2분 | 정상 |

응답 시간은 지표 수를 줄이기 위해 `0.1·0.3·0.5·1·3초` 구간만 수집하므로(`management.metrics.distribution.slo`) p95는 구간 단위의 근삿값입니다.

### 로그 수집

앱 로그는 컨테이너 표준 출력으로만 남아 배포 때 이전 컨테이너와 함께 지워지므로, Alloy가 Blue/Green 컨테이너 로그를 Grafana Cloud Loki(무료: 50GB, 14일 보관)로 보냅니다. 서버에는 `deploy.sh`의 `--log-opt`로 컨테이너당 최근 30MB만 남깁니다.

- 예외 스택 트레이스는 앞의 로그 한 줄에 묶어서 보냅니다.
- 라벨: `job="botem"`, `color`(blue/green), `level`(INFO·WARN·ERROR)
- 요청 ID는 값 종류가 많아 라벨이 아닌 구조화 메타데이터 `request_id`로 보냅니다.

설치 (Alloy 설치 후 1회):

1. Grafana Cloud에서 **Loki 쓰기 주소, 사용자 번호, `logs:write` 권한 토큰**을 발급받습니다 (Connections → Hosted logs).
2. 바뀐 `config.alloy`를 올리고 EC2에서 실행합니다.

```bash
sudo mv ~/config.alloy /etc/alloy/config.alloy
sudo usermod -aG docker alloy   # 도커 소켓에서 컨테이너 로그를 읽기 위한 권한
sudo tee -a /etc/default/alloy > /dev/null <<'ENV'
GRAFANA_CLOUD_LOKI_URL=<Loki 쓰기 주소 (…/loki/api/v1/push)>
GRAFANA_CLOUD_LOKI_USER=<사용자 번호>
GRAFANA_CLOUD_LOKI_TOKEN=<토큰>
ENV
sudo systemctl restart alloy
sudo journalctl -u alloy -n 30 --no-pager | grep -iE "error|warn" | grep -v udev
```

검색 (Grafana Cloud → Explore → 데이터 소스 `grafanacloud-…-logs`):

```logql
{job="botem"} | request_id="<프론트가 전달한 X-Request-Id>"
{job="botem", level="ERROR"}
{job="botem"} |= "Kakao"
```

