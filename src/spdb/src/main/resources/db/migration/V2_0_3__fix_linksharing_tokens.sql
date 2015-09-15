-- link sharing tokens MUST have some restrictions
-- this fixes existing tokens that were generated during a window when SP obtained
-- very broad tokens for linksharing purposes

-- remove all scopes (unlimited read and write)
delete AccessToken_scopes
from sp_url_sharing
join accesstoken on us_token=accesstoken.token
join AccessToken_scopes on ACCESSTOKEN_ID=accesstoken.id;

-- add linksharing scope
insert into AccessToken_scopes (ACCESSTOKEN_ID, scopes)
select accesstoken.id, "linksharing"
from sp_url_sharing
join accesstoken on us_token=accesstoken.token;

-- add read-only oid-bound scope
insert into AccessToken_scopes (ACCESSTOKEN_ID, scopes)
select accesstoken.id, concat("files.read:", lower(hex(us_sid)), lower(hex(us_oid)))
from sp_url_sharing
join accesstoken on us_token=accesstoken.token;
