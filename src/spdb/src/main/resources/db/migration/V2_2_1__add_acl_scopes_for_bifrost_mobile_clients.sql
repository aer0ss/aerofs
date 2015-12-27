-- Migration to add acl invite/read/write scopes for both iOS and android clients.
-- This is required to allow shared folder management and invite management
-- on mobile. 999991 is iOS and 999992 android.

INSERT INTO Client_scopes
    (CLIENT_ID, scopes)
VALUES
    (999991, 'acl.read'),
    (999991, 'acl.write'),
    (999991, 'acl.invitations');

INSERT INTO Client_scopes
    (CLIENT_ID, scopes)
VALUES
    (999992, 'acl.read'),
    (999992, 'acl.write'),
    (999992, 'acl.invitations');
