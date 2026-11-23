-- 사용자가 직접 삭제한 친구 관계. 카카오 친구 자동 연결(#248)이 로그인할 때 다시 맺지 않도록 남긴다
CREATE TABLE friend_removals
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    friend_id  BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_friend_removals_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_friend_removals_friend FOREIGN KEY (friend_id) REFERENCES users (id),
    CONSTRAINT uk_friend_removals_user_friend UNIQUE (user_id, friend_id),
    INDEX idx_friend_removals_friend_id (friend_id)
);
