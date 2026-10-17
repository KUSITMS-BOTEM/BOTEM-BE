# BOTEM Backend

## 프로젝트

- Kotlin 2.3, Spring Boot 4.1, JDK 21 기반 백엔드 프로젝트입니다.
- Gradle Kotlin DSL을 사용합니다.
- 주요 기술은 Spring MVC, Spring Data JPA, Spring Security, MySQL입니다.
- 기존 구조와 구현 방식을 먼저 확인하고 필요한 범위만 변경합니다.

## 작업 원칙

- 구현 전에 관련 코드와 기획 문서를 확인합니다.
- 요구사항에 없는 비즈니스 정책을 임의로 만들지 않습니다.
- 요구사항이 불명확하거나 서로 충돌하면 사용자에게 확인합니다.
- 문제와 직접 관련 없는 리팩터링이나 설정 추가를 피합니다.
- 시크릿, 비밀번호, 토큰 및 `.env` 파일을 커밋하지 않습니다.

## 검증

- 컴파일: `./gradlew compileKotlin`
- 테스트: `./gradlew test`
- 변경 범위에 맞는 검증을 수행하고, 실행하지 못한 검증은 명확히 알립니다.

## 추가 규칙

- Git 작업은 `.claude/rules/git-convention.md`를 따릅니다.
- Kotlin 및 Spring 코드는 `.claude/rules/code-convention.md`를 따릅니다.
- 기획 문서는 `.claude/rules/planning.md`를 따릅니다.

## 프로젝트 커맨드

- `/issue <작업 설명>`: GitHub Issue 초안 작성 및 생성
- `/branch <이슈 번호> <설명>`: Git Convention에 맞는 브랜치 생성
- `/commit`: 변경 사항 분석 및 커밋
- `/pr`: 현재 브랜치의 Pull Request 작성 및 생성
- `/review`: PR 생성 전 현재 변경 사항 리뷰
