-- 감사카드 작성 화면에서 고른 편지지 색상. 기존 카드는 화면 기본값인 LIME으로 채운다
ALTER TABLE contribution_thank_cards
    ADD COLUMN paper_color VARCHAR(20) NOT NULL DEFAULT 'LIME' AFTER message,
    ADD CONSTRAINT chk_contribution_thank_cards_paper_color
        CHECK (paper_color IN ('ORANGE', 'LIME', 'PINK', 'BLUE'));
