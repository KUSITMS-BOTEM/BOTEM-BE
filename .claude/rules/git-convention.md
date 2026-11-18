# Git Convention

## 브랜치

- Git Flow를 사용합니다.
- `feat`, `fix`, `refactor` 브랜치는 `develop`에서 분기하여 `develop`으로 병합합니다.
- `hotfix` 브랜치는 `main`에서 분기하고, 완료 후 `main`과 `develop`에 반영합니다.
- `main`과 `develop`에 직접 Push하지 않습니다.
- 하나의 브랜치는 하나의 GitHub Issue를 기준으로 생성합니다.
- 브랜치 이름은 `{type}/#{issue-number}-{description}` 형식을 사용합니다.
- 설명은 영문 소문자와 하이픈을 사용합니다.

예시:

```text
feat/#12-user-signup
fix/#34-token-validation
refactor/#45-user-service
hotfix/#56-login-error
```

## 커밋

- 커밋 메시지는 `<type>: <한글 설명>` 형식을 사용합니다.
- 설명은 명사형으로 간결하게 작성하고 마침표를 붙이지 않습니다.
- 주요 타입은 `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `build`, `ci`입니다.
- 관련 없는 변경은 하나의 커밋에 섞지 않습니다.
- 커밋 전에 변경 파일과 diff를 확인합니다.

### 작업 완료 후 커밋 추천

- 코드 작업과 검증을 마치면 커밋하지 않고 아래 내용을 함께 추천합니다.
  - 커밋 범위: 관련 있는 변경끼리 묶고, 각 커밋에 포함할 파일 목록
  - 커밋 메시지: 위 커밋 규칙을 따른 메시지
  - 명령어: 커밋별 `git add <파일>`과 `git commit -m "<메시지>"`를 바로 실행할 수 있는 하나의 코드 블록
- 커밋 순서는 구현 → API 명세 → 테스트처럼 기존 커밋 흐름을 따릅니다.
- 작업과 관련 없는 파일은 포함하지 않고, 제외한 파일이 있으면 이유를 알립니다.
- 사용자가 명시적으로 요청한 경우에만 실제로 커밋합니다.

## Pull Request

- 모든 코드 변경은 PR로 병합하고 관련 Issue를 연결합니다.
- 기능 개발 PR의 대상 브랜치는 `develop`입니다.
- CI 빌드와 테스트가 성공한 뒤 병합합니다.
- CodeRabbit 리뷰 중 버그, 보안, 데이터 정합성 관련 내용을 우선 검토합니다.
- CodeRabbit의 모든 제안을 반드시 반영할 필요는 없습니다.
- Merge Commit을 사용하며 Squash Merge와 Rebase Merge는 사용하지 않습니다.
- 병합 후 작업 브랜치를 삭제합니다.
