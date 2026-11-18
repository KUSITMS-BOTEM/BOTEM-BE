CREATE TABLE guest_contribution_access_attempts
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    funding_id       BIGINT       NOT NULL,
    participant_name VARCHAR(50)  NOT NULL,
    failed_count     INT          NOT NULL DEFAULT 0,
    locked_until     DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_guest_contribution_access_funding
        FOREIGN KEY (funding_id) REFERENCES fundings (id),
    CONSTRAINT uk_guest_contribution_access_funding_name
        UNIQUE (funding_id, participant_name),
    CONSTRAINT chk_guest_contribution_access_failed_count
        CHECK (failed_count BETWEEN 0 AND 4)
);
