CREATE TABLE group_member_history (
    group_id CHAR(32) CHARACTER SET LATIN1 NOT NULL,
    user_id VARCHAR(256) CHARACTER SET LATIN1 NOT NULL,
    caller_id VARCHAR(256) CHARACTER SET LATIN1 NOT NULL,
    time BIGINT NOT NULL, /* unix nano */
    added BOOLEAN, /* 1 = added, 0 = removed */
    FOREIGN KEY (group_id) REFERENCES groups (id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (caller_id) REFERENCES users (id)
);

CREATE INDEX group_member_history_index ON group_member_history (group_id, time);
