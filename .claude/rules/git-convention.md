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

## Pull Request

- 모든 코드 변경은 PR로 병합하고 관련 Issue를 연결합니다.
- 기능 개발 PR의 대상 브랜치는 `develop`입니다.
- CI 빌드와 테스트가 성공한 뒤 병합합니다.
- CodeRabbit 리뷰 중 버그, 보안, 데이터 정합성 관련 내용을 우선 검토합니다.
- CodeRabbit의 모든 제안을 반드시 반영할 필요는 없습니다.
- Merge Commit을 사용하며 Squash Merge와 Rebase Merge는 사용하지 않습니다.
- 병합 후 작업 브랜치를 삭제합니다.
