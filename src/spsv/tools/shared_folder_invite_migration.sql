--
-- Migration required to move away from invite codes to "pending" ACLs
--

-- create new column for pending bit
-- default to 0 (i.e. not pending) for existing entries
alter table sp_acl add column a_pending boolean not null default 0;

-- create new column for sharer
-- NB: temporarily allow null sharer during migration
alter table sp_acl add column a_sharer VARCHAR(320);

-- insert pending ACL entries for invite code that don't have an existing ACL entry
-- and update sharer field for those who do
insert into sp_acl(a_sid, a_id, a_role, a_pending, a_sharer)
    select
        f_share_id,
        f_to,
        f_role,
        1,                                                          -- insert as pending
        f_from
    from sp_shared_folder_code
    left outer join sp_acl as a
    on f_share_id=a.a_sid and f_to=a.a_id
    on duplicate key update
        a_role=if(sp_acl.a_role > f_role, sp_acl.a_role, f_role),   -- keep highest role
        a_sharer=f_from;

-- update sharer column for remaining ACL entries (i.e those of original sharers)
update sp_acl set a_sharer=a_id where a_sharer is null;

-- prevent new ACL entries from being created with a null sharer
alter table sp_acl modify a_sharer VARCHAR(320) not null;
