# 배포

`develop`에 병합되면 `.github/workflows/deploy.yml`이 이미지를 ECR에 push하고, EC2에서 `deploy.sh`로 Blue(8081)/Green(8082)을 전환합니다.

## EC2 최초 설정 (1회)

Docker, Nginx, AWS CLI를 설치하고 배포 사용자가 `docker`와 비밀번호 없는 `sudo`를 쓸 수 있게 합니다. EC2에는 ECR 읽기 권한이 있는 IAM Role을 연결합니다.

```bash
echo 'server 127.0.0.1:8081;' | sudo tee /etc/nginx/botem-upstream.inc
sudo cp deploy/nginx.conf /etc/nginx/conf.d/botem.conf
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

## 보안 그룹

- EC2: 80/443만 공개하고 22는 열지 않습니다. 배포 중에만 워크플로가 러너 IP를 허용합니다.
- RDS: EC2 보안 그룹에서 오는 3306만 허용합니다.

## GitHub Actions 역할 권한

GitHub OIDC를 신뢰하는 IAM Role에 다음 권한을 줍니다.

- ECR push (대상 저장소)
- 대상 보안 그룹의 `ec2:AuthorizeSecurityGroupIngress`, `ec2:RevokeSecurityGroupIngress`

## GitHub Secrets

| 구분 | 이름 |
|---|---|
| AWS | `AWS_ROLE_ARN`, `AWS_REGION`, `ECR_REPOSITORY`, `EC2_SECURITY_GROUP_ID` |
| SSH | `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `EC2_KNOWN_HOSTS` (`ssh-keyscan <host>` 결과를 확인 후 등록) |
| 앱 | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI`, `JWT_SECRET` |

## 스키마 변경

전환 중에는 이전 버전이 새 스키마로 동작합니다. 컬럼 삭제나 이름 변경은 컬럼 추가 → 코드 전환 → 다음 배포에서 삭제 순서로 나눕니다.

## 롤백

워크플로를 이전 커밋에서 다시 실행하거나, EC2에서 이전 이미지 태그로 실행합니다. Flyway로 바뀐 스키마는 되돌아가지 않습니다.

```bash
bash ~/botem/deploy.sh <ECR 이미지>:<이전 커밋 SHA>
```
