-- Migration to add required fields for group sharing. Step 2/4.

-- Add a group ID column to the ACL table, 0 meaning unrelated to any group
-- Change the primary key of the ACL table to be {share_id, user_id, group_id}.
ALTER TABLE sp_acl
    ADD COLUMN `a_gid` INTEGER NOT NULL DEFAULT 0,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (`a_sid`, `a_id`, `a_gid`);
