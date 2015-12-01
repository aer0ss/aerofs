CREATE TABLE pinned (
    user_id VARCHAR(256) CHARACTER SET LATIN1 NOT NULL,
    convo_id VARCHAR(256) CHARACTER SET LATIN1 NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id),
    PRIMARY KEY (user_id, convo_id)
);
