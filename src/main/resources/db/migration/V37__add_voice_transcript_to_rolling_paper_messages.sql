-- 롤링페이퍼 음성 아래에 보여주는 자막. 프론트가 만들어 보내며 음성이 있을 때만 저장한다
ALTER TABLE rolling_paper_messages
    ADD COLUMN voice_transcript VARCHAR(500) NULL AFTER voice_duration_seconds;

ALTER TABLE rolling_paper_messages
    ADD CONSTRAINT chk_rolling_paper_messages_voice_transcript
        CHECK (voice_transcript IS NULL OR voice_url IS NOT NULL);
