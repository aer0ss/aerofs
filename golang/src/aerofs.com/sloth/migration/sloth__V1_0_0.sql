CREATE TABLE users (
    id VARCHAR(256) CHARACTER SET LATIN1 PRIMARY KEY, /* email */
    first_name VARCHAR(128) CHARACTER SET UTF8 NOT NULL,
    last_name VARCHAR(128) CHARACTER SET UTF8 NOT NULL
);

CREATE TABLE groups (
    id CHAR(32) CHARACTER SET LATIN1 PRIMARY KEY, /* 128 bits hex-encoded */
    created_time BIGINT NOT NULL, /* unix nano */
    name VARCHAR(128) CHARACTER SET UTF8 NOT NULL,
    is_public BOOLEAN NOT NULL
);

CREATE TABLE group_members (
    user_id VARCHAR(256) CHARACTER SET LATIN1 NOT NULL,
    group_id CHAR(32) CHARACTER SET LATIN1 NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (group_id) REFERENCES groups (id),
    PRIMARY KEY (user_id, group_id)
);

CREATE TABLE messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, /* TODO: random id to not leak # of messages? */
    time BIGINT NOT NULL, /* unix nano */
    body TEXT CHARACTER SET UTF8 NOT NULL,
    from_id VARCHAR(256) CHARACTER SET LATIN1 NOT NULL,
    to_id VARCHAR(256) CHARACTER SET LATIN1 NOT NULL, /* size: max(groups.id, users.id) */
    FOREIGN KEY (from_id) REFERENCES users (id)
);

CREATE INDEX message_from_to ON messages(to_id, from_id);
