-- With pending members being possible in groups, we can no longer require that
-- the member emails already exist in our DB

ALTER TABLE `sp_sharing_group_members` DROP FOREIGN KEY gm_member_id_foreign;
