# BOTEM-BE

BOTEM 백엔드 서버입니다.

- 📐 [아키텍처와 기술 선택 이유](docs/architecture.md): 시스템·배포 구조, 주요 기술을 고른 이유와 해결한 문제, 확장 계획
- 📈 [부하 테스트 결과](loadtest/README.md): 핵심 API 처리량, 동시 보태기 정합성, 쿼리 실행계획

# 🛠️ Tech Stack

### 💻 Language & Framework

- **Language** `Kotlin`
- **JDK** `Java 21`
- **Framework** `Spring Boot 4.1`
- **Web** `Spring Web MVC`

### 🔐 Security

- **Authentication & Authorization** `Spring Security`
- **Token** `JWT`

### 🗄️ Database & Persistence

- **RDBMS** `MySQL`
- **ORM** `Spring Data JPA`
- **Database Migration** `Flyway`

### ☁️ Infrastructure & Storage

- **Cloud Platform** `AWS`
- **Compute** `EC2`
- **Database** `RDS`
- **Object Storage** `S3`
- **Container Registry** `ECR`

### 🚀 CI/CD

- **CI/CD Pipeline** `GitHub Actions`
- **Containerization** `Docker`

### 📚 API Documentation

- **API Specification** `Springdoc OpenAPI`
- **API Documentation UI** `Swagger UI`

### 🧪 Testing & Code Quality

- **Testing** `JUnit 5` · `Spring Test` · `Testcontainers (MySQL)`
- **Load Testing** `k6`
- **Code Coverage** `Kover`
- **Automated Code Review** `CodeRabbit`

### 📊 Monitoring

- **Error Tracking** `Sentry`
- **Metrics Collection** `Prometheus` (도입 예정, #205)
- **Monitoring & Visualization** `Grafana` (도입 예정, #205)

# 🗂️ ERD

실제 MySQL에 Flyway 마이그레이션을 적용한 스키마로 자동 생성합니다. 직접 수정하지 말고 `./gradlew generateErd`(Docker 필요)로 갱신하세요. 엔티티·마이그레이션이 바뀐 PR에서는 CI가 자동으로 갱신해 커밋합니다.

<!-- ERD:START -->
```mermaid
erDiagram
    api_integration_statuses {
        bigint id PK
        varchar http_method
        varchar path
        varchar owner "nullable"
        varchar frontend_owner "nullable"
        tinyint integrated
        datetime created_at
        datetime updated_at
    }
    contribution_thank_cards {
        bigint id PK
        bigint contribution_id FK, UK
        bigint sender_user_id FK
        varchar message
        varchar paper_color
        varchar share_key UK
        datetime sent_at
        datetime created_at
        datetime updated_at
    }
    contributions {
        bigint id PK
        bigint funding_id FK
        bigint participant_user_id FK "nullable"
        varchar participant_name
        bigint amount
        varchar status
        varchar guest_password_hash "nullable"
        int guest_password_failed_count
        datetime guest_password_locked_until "nullable"
        varchar request_key "nullable"
        datetime confirmed_at "nullable"
        datetime rejected_at "nullable"
        datetime created_at
        datetime updated_at
    }
    friend_removals {
        bigint id PK
        bigint user_id FK
        bigint friend_id FK
        datetime created_at
        datetime updated_at
    }
    friendships {
        bigint id PK
        bigint user_id FK
        bigint friend_id FK
        datetime created_at
        datetime updated_at
    }
    funding_product_change_histories {
        bigint id PK
        bigint funding_id FK
        bigint previous_source_wish_id "nullable"
        varchar previous_product_name
        varchar previous_product_image_url
        bigint previous_product_price
        varchar previous_product_url
        bigint changed_source_wish_id "nullable"
        varchar changed_product_name
        varchar changed_product_image_url
        bigint changed_product_price
        varchar changed_product_url
        datetime changed_at
    }
    fundings {
        bigint id PK
        bigint owner_id FK
        bigint recipient_id FK "nullable"
        varchar funding_type
        varchar status
        varchar title "nullable"
        bigint target_amount
        datetime deadline
        datetime reveal_at "nullable"
        varchar story "nullable"
        varchar promise "nullable"
        tinyint show_on_recipient_profile
        tinyint notify_recipient_friends
        varchar share_key UK
        bigint source_wish_id FK "nullable"
        varchar product_name
        varchar product_image_url
        bigint product_price
        varchar product_url
        varchar product_seller_name "nullable"
        bigint source_payout_account_id FK "nullable"
        varchar payout_bank
        varchar payout_encrypted_account_number
        varchar payout_account_holder "nullable"
        int extension_count
        datetime extended_at "nullable"
        datetime result_opened_at "nullable"
        datetime gift_prepared_at "nullable"
        datetime completed_at "nullable"
        datetime created_at
        datetime updated_at
        datetime surprise_opened_at "nullable"
        datetime early_closed_at "nullable"
        datetime product_changed_at "nullable"
        bigint recommended_amount "nullable"
    }
    gift_verifications {
        bigint id PK
        bigint funding_id FK, UK
        varchar status
        varchar image_url "nullable"
        longtext thank_you_message "nullable"
        tinyint purchased_through_botem_link
        varchar replacement_product_name "nullable"
        datetime completed_at "nullable"
        datetime created_at
        datetime updated_at
    }
    guest_contribution_access_attempts {
        bigint id PK
        bigint contribution_id FK, UK
        int failed_count
        datetime locked_until "nullable"
        datetime created_at
        datetime updated_at
    }
    payout_accounts {
        bigint id PK
        bigint user_id FK
        varchar bank
        varchar encrypted_account_number
        varchar account_holder "nullable"
        tinyint is_default
        varchar verification_status
        datetime created_at
        datetime updated_at
    }
    product_requests {
        bigint id PK
        bigint user_id FK
        varchar product_url
        varchar coupang_product_id "nullable"
        varchar product_name "nullable"
        datetime created_at
        datetime updated_at
    }
    recommended_products {
        bigint id PK
        varchar name
        varchar image_url
        bigint price
        varchar seller_name
        varchar product_url
        varchar category
        varchar status
        int display_order
        datetime created_at
        datetime updated_at
    }
    refresh_tokens {
        bigint id PK
        bigint user_id FK, UK
        varchar token_hash
        datetime created_at
        datetime updated_at
    }
    rolling_paper_messages {
        bigint id PK
        bigint contribution_id FK, UK
        longtext content "nullable"
        varchar paper_template_code
        varchar decoration_code "nullable"
        varchar photo_url "nullable"
        varchar voice_url "nullable"
        int voice_duration_seconds "nullable"
        varchar voice_transcript "nullable"
        datetime created_at
        datetime updated_at
    }
    surprise_thank_cards {
        bigint id PK
        bigint funding_id FK, UK
        bigint sender_user_id FK
        longtext message
        datetime sent_at
        datetime created_at
        datetime updated_at
    }
    users {
        bigint id PK
        bigint kakao_id UK
        varchar nickname
        varchar profile_image_url "nullable"
        date birthday "nullable"
        datetime created_at
        datetime updated_at
        varchar wish_profile_key UK "nullable"
        tinyint kakao_connected
        varchar status
        datetime withdrawn_at "nullable"
        varchar birth_month_day "nullable"
        varchar name "nullable"
        varchar email "nullable"
    }
    wishes {
        bigint id PK
        bigint user_id FK
        varchar product_name
        varchar product_image_url
        bigint price
        varchar product_url
        datetime created_at
        datetime updated_at
        bigint recommended_product_id FK "nullable"
        varchar seller_name "nullable"
    }
    contributions ||--o| contribution_thank_cards : "contribution_id"
    contributions ||--o| guest_contribution_access_attempts : "contribution_id"
    contributions ||--o| rolling_paper_messages : "contribution_id"
    fundings ||--o{ contributions : "funding_id"
    fundings ||--o{ funding_product_change_histories : "funding_id"
    fundings ||--o| gift_verifications : "funding_id"
    fundings ||--o| surprise_thank_cards : "funding_id"
    payout_accounts |o--o{ fundings : "source_payout_account_id"
    recommended_products |o--o{ wishes : "recommended_product_id"
    users ||--o{ contribution_thank_cards : "sender_user_id"
    users |o--o{ contributions : "participant_user_id"
    users ||--o{ friend_removals : "friend_id"
    users ||--o{ friend_removals : "user_id"
    users ||--o{ friendships : "friend_id"
    users ||--o{ friendships : "user_id"
    users ||--o{ fundings : "owner_id"
    users |o--o{ fundings : "recipient_id"
    users ||--o{ payout_accounts : "user_id"
    users ||--o{ product_requests : "user_id"
    users ||--o| refresh_tokens : "user_id"
    users ||--o{ surprise_thank_cards : "sender_user_id"
    users ||--o{ wishes : "user_id"
    wishes |o--o{ fundings : "source_wish_id"
```
<!-- ERD:END -->

# 🌿 Git Convention

## 1. Branch Strategy

Git Flow 전략을 기반으로 브랜치를 관리

### 브랜치 종류

| 브랜치 | 설명 | 분기 기준 | 병합 대상 |
| --- | --- | --- | --- |
| `main` | 운영 환경 배포 | - | - |
| `develop` | 개발 기능 통합 | `main`에서 최초 생성 | `main` |
| `feat` | 새로운 기능 개발 | `develop` | `develop` |
| `fix` | 개발 중 발생한 버그 수정 | `develop` | `develop` |
| `refactor` | 기존 코드 리팩토링 | `develop` | `develop` |
| `docs` | 문서 작성 및 수정 | `develop` | `develop` |
| `test` | 테스트 코드 추가 및 수정 | `develop` | `develop` |
| `chore` | 빌드, 설정 및 기타 변경 | `develop` | `develop` |
| `hotfix` | 운영 환경 긴급 오류 수정 | `main` | `main`, `develop` |

### 브랜치 네이밍

`{type}/#{issue-number}-{description}`

```
feat/#12-user-signup
fix/#34-token-validation
refactor/#45-user-service
hotfix/#56-login-error
```

- 브랜치 이름은 영문 소문자를 사용합니다.
- 단어 구분은 하이픈(-)을 사용합니다.
- GitHub Issue 번호를 포함합니다.
- 하나의 브랜치는 하나의 Issue를 기준으로 생성합니다.

### 브랜치 운영 규칙

1. 모든 기능 개발은 `develop`에서 브랜치를 생성합니다.
2. 개발 완료 후 `develop`을 대상으로 PR을 생성합니다.
3. `develop`에서 기능 통합 및 테스트를 진행합니다.
4. 배포 시 `develop`의 검증된 변경 사항을 `main`에 병합합니다.
5. 운영 환경 긴급 수정은 `main`에서 `hotfix` 브랜치를 생성합니다.
6. `hotfix` 수정 사항은 `main`과 `develop` 모두에 반영합니다.
7. `main`, `develop`에 직접 Push하지 않고 PR을 통해 병합합니다.

---

## 2. Pull Request & Merge Strategy

### PR 운영 규칙

- 모든 코드 변경은 PR을 통해 병합합니다.
- PR에는 관련 Issue를 연결합니다.
- PR 생성 시 CodeRabbit 자동 리뷰를 진행합니다.
- CodeRabbit의 리뷰 내용을 확인하고 필요한 사항을 수정합니다.
- 팀원 Approve는 필수로 요구하지 않습니다.
- CI 빌드 및 테스트가 성공해야 병합할 수 있습니다.
- PR 병합 완료 후 작업 브랜치를 삭제합니다.

### Merge Strategy

**Create a merge commit 사용**

- Squash Merge를 사용하지 않습니다.
- Rebase Merge를 사용하지 않습니다.
- 기능 브랜치의 개별 커밋 이력을 유지합니다.
- PR 병합 시 Merge Commit을 생성합니다.

### Code Review

CodeRabbit을 활용하여 자동 코드 리뷰를 진행합니다.

**검토 항목**

- 잠재적인 버그 및 예외 처리 누락
- 보안 취약점
- 성능 문제
- 코드 중복 및 가독성
- 코드 컨벤션 준수 여부

**리뷰 운영 기준**

- CodeRabbit의 모든 제안을 반드시 반영할 필요는 없습니다.
- 버그, 보안, 데이터 정합성 관련 지적은 우선 검토합니다.
- 리뷰 결과에 대한 최종 판단은 PR 작성자가 수행합니다.
- 사람의 Approve 없이도 병합할 수 있습니다.
