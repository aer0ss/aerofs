DROP TABLE IF EXISTS group_member_history;
ALTER TABLE bots DROP FOREIGN KEY bots_ibfk_1;
ALTER TABLE group_members DROP FOREIGN KEY group_members_ibfk_2;

-- convo type is CHANNEL (1), DIRECT (2), OBJECT (3)
ALTER TABLE groups ADD COLUMN type TINYINT NOT NULL DEFAULT 1;
ALTER TABLE groups ADD COLUMN sid BINARY(16) NULL;
ALTER TABLE groups MODIFY COLUMN id VARCHAR(32);
RENAME TABLE groups TO convos;

ALTER TABLE group_members CHANGE COLUMN group_id convo_id VARCHAR(32) NOT NULL;
RENAME TABLE group_members TO convo_members;

ALTER TABLE bots CHANGE COLUMN group_id convo_id VARCHAR(32) NOT NULL;

-- TODO: re-create foreign keys
