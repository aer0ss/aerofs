--
-- The system currently does not support actual removal of DID and UserID and instead flips
-- a bit to indicate invalidity. This is necessary to keep logging/auditing (e.g. activity
-- log) working.
--

alter table sp_user add column u_deactivated boolean not null;
update sp_user set u_deactivated=0;
