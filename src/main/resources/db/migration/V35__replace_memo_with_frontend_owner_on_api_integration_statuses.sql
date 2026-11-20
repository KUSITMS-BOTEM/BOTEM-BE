-- API 연동 현황 보드: 메모 칸을 없애고 프론트 연동 담당자를 저장한다 (기존 메모는 함께 삭제)
ALTER TABLE api_integration_statuses
    DROP COLUMN memo,
    ADD COLUMN frontend_owner VARCHAR(20) NULL AFTER owner;
