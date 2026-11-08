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

이어서 HTTPS 인증서를 발급합니다. `api.botem.site`의 DNS가 EC2를 가리키고 80/443이 열려 있어야 합니다.

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.botem.site -m <이메일> --agree-tos --redirect -n
```

위 절차는 새 서버에서만 실행합니다. 운영 중인 서버에서 다시 실행하면 Certbot HTTPS 설정과 활성 upstream이 덮어써집니다. `deploy/nginx.conf`는 배포 시 자동 반영되지 않으므로, 운영 설정을 바꿀 때는 `/etc/nginx/conf.d/botem.conf`를 직접 수정하고 `sudo nginx -t && sudo systemctl reload nginx`를 실행합니다.

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
| 앱 | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI`, `JWT_SECRET`, `PAYOUT_ACCOUNT_ENCRYPTION_KEY`, `AUTH_COOKIE_DOMAIN` (`.botem.site`) |

`PAYOUT_ACCOUNT_ENCRYPTION_KEY`는 16, 24 또는 32바이트 난수를 Base64로 인코딩해 등록합니다. 운영 중 키를 잃거나 임의로 교체하면 저장된 계좌번호를 복호화할 수 없습니다.

## 스키마 변경

전환 중에는 이전 버전이 새 스키마로 동작합니다. 컬럼 삭제나 이름 변경은 컬럼 추가 → 코드 전환 → 다음 배포에서 삭제 순서로 나눕니다.

## 롤백

워크플로를 이전 커밋에서 다시 실행하거나, EC2에서 이전 이미지 태그로 실행합니다. Flyway로 바뀐 스키마는 되돌아가지 않습니다.

```bash
bash ~/botem/deploy.sh <ECR 이미지>:<이전 커밋 SHA>
```
