CREATE TABLE users
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    kakao_id          BIGINT       NOT NULL,
    nickname          VARCHAR(255) NOT NULL,
    profile_image_url VARCHAR(255) NULL,
    birthday          DATE         NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_kakao_id UNIQUE (kakao_id)
);
