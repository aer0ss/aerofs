CREATE TABLE last_read (
    user_id VARCHAR(256) CHARACTER SET LATIN1,
    convo_id VARCHAR(256) CHARACTER SET LATIN1,
    msg_id BIGINT,
    time BIGINT NOT NULL, /* unix nano */
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (msg_id) REFERENCES messages (id),
    PRIMARY KEY (convo_id, user_id)
);
