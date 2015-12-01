CREATE TABLE bots (
    id CHAR(32) CHARACTER SET LATIN1 PRIMARY KEY, /* 128 bits hex-encoded */
    name VARCHAR(128) CHARACTER SET UTF8 NOT NULL,
    group_id CHAR(32) CHARACTER SET LATIN1,
    avatar BLOB,
    FOREIGN KEY (group_id) REFERENCES groups (id)
);

ALTER TABLE messages DROP FOREIGN KEY messages_ibfk_1;
