--
-- Rename old OAuth scopes to be more descriptive and consistent with newly introduced scopes
--

update Client_scopes set scopes='files.read' where scopes='read';
update Client_scopes set scopes='files.write' where scopes='write';

update ResourceServer_scopes set scopes='files.read' where scopes='read';
update ResourceServer_scopes set scopes='files.write' where scopes='write';

update AuthorizationRequest_requestedScopes
    set requestedScopes='files.read' where requestedScopes='read';
update AuthorizationRequest_requestedScopes
    set requestedScopes='files.write' where requestedScopes='write';

update AuthorizationRequest_grantedScopes
    set grantedScopes='files.read' where grantedScopes='read';
update AuthorizationRequest_grantedScopes
    set grantedScopes='files.write' where grantedScopes='write';

update AccessToken_scopes set scopes='files.read' where scopes='read';
update AccessToken_scopes set scopes='files.write' where scopes='write';
