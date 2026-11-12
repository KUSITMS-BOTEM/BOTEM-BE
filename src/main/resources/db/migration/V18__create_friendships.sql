-- 친구 관계는 양방향 두 행으로 저장해 내 친구 목록을 user_id 하나로 조회한다
CREATE TABLE friendships
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    friend_id  BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_friendships_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_friendships_friend FOREIGN KEY (friend_id) REFERENCES users (id),
    CONSTRAINT uk_friendships_user_friend UNIQUE (user_id, friend_id),
    INDEX idx_friendships_friend_id (friend_id)
);
