-- SP had a bug wherein it did not correctly revoke access tokens when
-- updating linksharing URLs (adding password, changing expiry, ...)

-- delete all accesstokens issued for aerofs-zelda (linksharing) and not
-- associated with any existing URL
delete accesstoken
from accesstoken
join client on accesstoken.client_id=client.id
left join sp_url_sharing on accesstoken.token=us_token
where client.clientId='aerofs-zelda' and us_token is NULL;

-- delete all dangling scopes (left by the above removal of tokens)
delete AccessToken_scopes
from AccessToken_scopes
left join accesstoken on ACCESSTOKEN_ID = accesstoken.id
where accesstoken.id is NULL;

