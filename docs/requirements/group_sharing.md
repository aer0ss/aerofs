# AeroFS Group Sharing

---

## Use Cases

* Admins/users can share folders with a group instead of inviting individual users.
* Users join a folder as soon as they join a group, and leave a folder as soon as they leave a
folder.

## Requirements

### Groups
* Groups can be created by Admins.
* Groups can be synchronized with external system like AD.
* Groups **cannot** be managed by non-admin users (simplifies UX).

### Admin
* Admin can share and unshare folders with groups.
* Admin can create, modify, delete locally-managed groups.
* Admin can set permissions to a group for a given shared folder.
* Admin **cannot** modify externally-managed groups using the AeroFS interface.
* FUTURE: Admin can delete/ignore/discard externally managed groups.

### User
* User can share and unshare a folder they are an owner of with a group.
* User can set permissions in a folder they are an owner of to a group.

### Group Modification
* A user that joins a group will be invited to the folders this group has access to. The user can
then choose to accept or ignore the invitation via the normal web interface.
* A user that leaves a group must leave the folders this group has access to.

### Permission Precedence
* If a user belongs to a group that shares a folder, the user has access to this folder from another
share, and permissions differ between the two shares, user permission on the folder is the highest
of the two. Example: a user joins a group that is Viewer on a folder, and is also Editor on this
folder. User has an Editor permission on the folder.

### Organizations
* Groups **cannot** cross organizations (this also serves as an incentive to pay us in HC).

### Reconciliation
* The AeroFS Group Management service reconciles the external AD store and the AeroFS group database
once per day.
* An organization admin can request an immediate reconciliation via the web admin UI.

---

## Interface

### Admin

* Admin group management page: admin can perform the following actions for locally-managed groups:
create, modify, delete.
* Schedule reconciliation if applicable.

### User

* Group browser: user can browse existing groups and group members.
* Shared folder management: user set permissions in a folder they are an owner of to a group (very
similar to the existing 'Manage Shared Folder' dialog). The applies to both desktop and web.

### Auto-complete

* FUTURE: User should be able be able to auto-complete search for groups when sharing/managing from
desktop/web.
