--
-- Move from discrete roles to more granular independent boolean flags
--
-- Before:
--   Viewer < Editor < Owner
--
-- After:
--   Baseline:
--     Read
--   Flags:
--     Write
--     Manage
--

-- VIEWER = 0 -> 0
-- EDITOR = 1 -> 1  = WRITE
-- OWNER  = 2 -> 3  = WRITE | MANAGE
update sp_acl set a_role=3 where a_role=2;
