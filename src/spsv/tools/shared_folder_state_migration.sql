--
-- Migration required to change from a boolean-based 2-state pending flag to an
--   enum based 3-state pending state.
--

-- create new column for shared folder state
alter table sp_acl add column a_state tinyint not null;

-- populate the shared folder state column based on the pending flag
update sp_acl set a_state=if(a_pending, 1, 0);

-- drop the column for pending flag
alter table sp_acl drop column a_pending;
