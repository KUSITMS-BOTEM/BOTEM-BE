# Planning Documents

- 별도 GitHub 저장소 `BOTEM-PLAN`을 기획 문서의 기준으로 사용합니다.
- `BOTEM-PLAN`의 문서는 GitHub Actions로 이 저장소에 자동 동기화됩니다.
  - `PL/docs/` → `.claude/docs/plan/` (기획 문서)
  - `BE/` → `.claude/docs/be/` (백엔드 파트 문서)
- 작업 전에 `.claude/docs/plan/`과 `.claude/docs/be/`에서 관련 문서를 먼저 확인합니다.
- 기획 문서에 없는 정책을 임의로 추가하지 않습니다.
- 기획 문서와 현재 구현이 충돌하면 임의로 선택하지 않고 사용자에게 알립니다.
- 동기화된 문서는 이 저장소에서 직접 수정하지 않습니다. 수정은 `BOTEM-PLAN`에서 하고 동기화 PR로 반영합니다.
