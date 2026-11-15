CREATE TABLE rolling_paper_messages
(
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    contribution_id        BIGINT        NOT NULL,
    content                LONGTEXT      NULL,
    paper_template_code    VARCHAR(50)   NOT NULL,
    decoration_code        VARCHAR(50)   NULL,
    photo_url              VARCHAR(2048) NULL,
    voice_url              VARCHAR(2048) NULL,
    voice_duration_seconds INT           NULL,
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rolling_paper_messages_contribution UNIQUE (contribution_id),
    CONSTRAINT fk_rolling_paper_messages_contribution
        FOREIGN KEY (contribution_id) REFERENCES contributions (id),
    CONSTRAINT chk_rolling_paper_messages_content
        CHECK (
            (content IS NOT NULL AND CHAR_LENGTH(TRIM(content)) > 0)
            OR decoration_code IS NOT NULL
            OR photo_url IS NOT NULL
            OR voice_url IS NOT NULL
        ),
    CONSTRAINT chk_rolling_paper_messages_voice
        CHECK (
            (voice_url IS NULL AND voice_duration_seconds IS NULL)
            OR (voice_url IS NOT NULL AND voice_duration_seconds > 0)
        )
);
