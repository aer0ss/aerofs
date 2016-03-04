/* Add the creator, time created to the bots table */
ALTER TABLE bots ADD creator_id VARCHAR(256) CHARACTER SET LATIN1;
ALTER TABLE bots ADD creation_time BIGINT NULL;
ALTER TABLE bots ADD CONSTRAINT fk_creator_id FOREIGN KEY (creator_id) REFERENCES users(id);
