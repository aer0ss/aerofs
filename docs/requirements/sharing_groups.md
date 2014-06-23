# AeroFS Sharing Groups

[//]: <> (6/18 - v2, first draft w/ use cases)
[//]: <> (v1, initial draft. Based on conversation w/ prospect)

## Use Cases

- Admin can invite a group instead of inviting individual users.
- Users can share folders with a group instead of inviting individual users.
- Users can join a folder as soon as they join a group, and leave a folder as soon as they leave a folder.

## Groups
- Groups can come from AeroFS users, including admin.
- Groups can come from from an external system like LDAP.

## Admin
- Admin can create, modify, delete a group.
- Admin can share, and unshare folders with groups.
- Admin can set permissions to a group for a given shared folder.
- Admin can add, remove a user to a group.

## User
- User can create a group.
- User can modify, delete a group she created.
- User can share and unshare a folder she is an owner of, with a group.
- User can set permissions in a folder she is an owner of, to a group.
- User can add, remove a user to a group she created.

## Group Modification
- A user that joins a group should join the folders this group has access to.
- A user that leaves a group should leave the folders this group has access to.

## Permission Precedence
- If a user belongs to a group that shares a folder, the user has access to this folder from another share, and permissions differ between the two shares, user permission on the folder is the highest of the two. e.g.: a user joins a group that is Viewer on a folder, and is also Editor on this folder. User has an Editor permission on the folder.

## Organizations
- Groups cannot cross organizations.

***

# Historical requirements
By Jon Pile

ActiveDirectory (and other LDAP identity servers) provide generalized support for defining and managing groups. Users may belong to many overlapping groups, such as "the finance team", "West Coast personnel", and "Left-handed Albanian potato farmers".

When a customer deploys AeroFS and uses their existing AD infrastructure, they would like to leverage the work they have already put in to defining and maintaining these groups. This is believed to be more acute in larger deployments, and those with more highly-structured LDAP records.

Admins would like to create folders and assign them to teams or distribution lists. Users would like to enter team names instead of individual names.

## Entities

The Group Association is a new relational object consisting of:

 - Unique identifier (opaque, internal only).
 - LDAP group name.
 - AeroFS Shared Folder name.
 - Default role `{Owner, Publisher, Viewer}`.

It must be possible to iterate the set of users last affected by a given Group Association. (This may be thought of as part of the shared-folder membership or as a list of users in the Group Association).

## Sharing

`REQ` For any AeroFS Shared Folder, the Organization Admin can add an association with an LDAP group.

`REQ` Each association between a Shared Folder and an LDAP group has a "default role" which is any AeroFS role; one of `{Owner, Publisher, Viewer}`.

`REQ` An Organization Admin can view the current set of LDAP groups associated with each shared folder, and what their default AeroFS role is for that folder.

## Group Reconciliation

`REQ` For each Group Association, the Group Management service must reconcile the external LDAP group with the shared folder.

 - Users of the group that do not belong to the shared folder will be added with the appropriate role.

 - Users that belong to the shared folder but with a lower role will be promoted to the role defined by the group association.

 - Users that have been removed from the LDAP group but already belong to the shared folder will be evicted from the shared folder.

 - Users that belong to a shared folder as a result of a personal invite - i.e. not as part of an LDAP group - must not be evicted.

This implies that the service can distinguish shared-folder membership for an individual from shared-folder membership as a part of a Group Association.

Further, this implies the service can iterate the users whose membership is the result of a given Group Association.

## Scheduled Reconciliation

`REQ` The AeroFS Group Management service reconciles the external LDAP store and the AeroFS shared folders automatically once per day.

`REQ` An Organization Admin can request an immediate reconciliation via the web admin UI.

`REQ` Deleting a Group Association or changing the default role will result in immediate reconciliation - all users belonging to the LDAP group will be removed from the associated shared folder.

## Administration

Administration of this feature is through the web administration console.

`REQ` An Organization Admin can view a list of Group Associations in use in their organization, even if they do not belong to the shared folder.
			
`REQ` The Admin can modify the default role for each Group Association.

`REQ` The Admin can remove a Group Association.
