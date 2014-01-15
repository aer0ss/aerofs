-- change 'read' to 'readonly' everywhere in bifrost
UPDATE Client_scopes SET scopes='read' WHERE scopes='readonly';
UPDATE ResourceServer_scopes SET scopes='read' WHERE scopes='readonly';
UPDATE AccessToken_scopes SET scopes='read' WHERE scopes='readonly';

-- add 'write' permission
INSERT INTO ResourceServer_scopes VALUES (999999, 'write');
INSERT INTO Client_scopes VALUES (999991, 'write');
