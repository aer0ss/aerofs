# Data Leak Prevention - Auditable Events

    Current revision: 0.5
    Revision history:
      0.1 Initial draft
      0.2 Clarify channel configuration, offline client access.
      0.3 Make corrections to latency, offline access requirements.
      0.4 Update timer resolution, sharing event types.
      0.5 Rework to reflect service as-built


## Purpose

System administrators should be able to determine usage and sharing patterns for their organization. This includes near-real-time status information as well as historical data.

The purpose of the audit feature in the user's organization are:

 - enable insights into how data is shared and accessed;

 - provide near-real-time monitoring of the flow of data through the organization, so the administrator can detect and prevent data leakage;

 - help retroactively identify the root causes of data leakage.

We provide these features by publishing auditable events to existing IT infrastructure for event management.


## AeroFS Audit Service

The AeroFS Audit Service enables all the other features described in this document. It is the central point that accepts auditable events from various sources, applies formatting as needed, and delivers the events to one or more downstream systems.

The audit service is not required to provide indefinite local buffering of auditable events. This service assumes that downstream systems are reliable.

## Auditable Events from Clients

*Note:* We call an AeroFS client "offline" if it is unable to reach the AeroFS Appliance; for instance, in a segregated external network. An offline client may still perform local-network file transfers with certified peers.

If client file-transfer auditing is enabled, AeroFS clients will upload the file transfer events to an audit collection service on the AeroFS Appliance. Clients cannot locally opt-out.

Activity logs of local-network file transfer events will be batch-uploaded to the AeroFS Appliance when the client is next online.

## Channels: Delivery to downstream Systems

Auditable events must be delivered to a downstream event capture and analysis system. The site administrator will choose the destination system or systems.

The channel encapsulates configuration of the transport, network address, and message protocol to deliver.

The message protocols supported today are JSON documents sent over a TCP or TCP-SSL stream. In the case of the TCP-SSL stream, the administrator can supply an explicit certificate in PEM format for the downstream system.

This channel can be used to connect to a Splunk server on a network listener port.



## Event Contents

All audit events include the following:

 - `topic` : The coarse topic the event belongs to. Must be one of { "FILE", "USER", "SHARING", "DEVICE" }.

 - `event` : The topic name of the event. The set of events are described below in Event Topics.

 - `timestamp` : Time the event was published. This may differ from the time received by the downstream system.

Additional parameters are documented with the events, grouped by topic, below.



## Type Dictionary

This section describes some of the object types used by the audit system.

#####  <a name="soid"></a> SOID

A Store and Object Identifier. This uniquely identifies a single file or folder resource within the AeroFS device network. This object contains two elements:

 - `sid` : Store ID

 - `oid` : Object ID

A file (or folder) identifier suitable for use with the AeroFS Content API can be constructed by concatenating `sid` and `oid`.

Sample JSON representation of a SOID:

	{"sid":"e4c357190e9d314ca135d6f1f6938d9b","oid":"4e0375957f304b9bbfcddbf87f10b5d2"}


##### <a name="sid"></a> SID

The SID field uniquely identifies a Store. A Store is a folder container that has an owner and a set of permission. A store could be either the user's root store (i.e. the AeroFS folder) or a shared folder.


##### <a name="oid"></a> OID

The OID field represents the Object identifier within a given Store. The OID is an opaque identifier that follows a single file or folder, even across renames. (This is why we do not simply track path names).

Note that the SID and OID are used to query and modify files and folders using the AeroFS Content API.


##### <a name="did"></a> DID

A Device Identifier is a unique identifier generated for a particular AeroFS device each time it certifies with the AeroFS Private Cloud. If a user unlinks a computer and subsequently re-certifies with AeroFS, that will generate a second DID.

To correlate events that report DID to a particular user, search backward for the `device.certify` event.

A user can determine the DID for their AeroFS installation; see https://support.aerofs.com/entries/25449638 . Also note that the Network Diagnostics view reports connected peers by DID.

Mobile devices (iOS) and applications that are authorized using the Content API are also assigned a DID.

##### <a name="caller"></a> Caller

Identity of the user that authorized a particular action. This is derived from the access token provided with an API call.

If the access token was issued with administrator scope, the `acting_as` field will be provided.  This field indicates the authority that was used to perform the action. In this case, the `email` field provides the user that created the access token.

 - `email` : The user that authorized the action by creating the access token.

 - `device` : Unique identifier of the app or mobile device that initiated the action.

 - `acting_as` : If provided, the user ID for whom the access token was granted. Administrators may authorize a privileged access token; in this case the `acting_as` value will be a specially-formatted administrator id.

Sample JSON representation of a Caller, followed by a privileged Caller object:

	{"email":"jon@aerofs.com","device":"40f9cddab98a432b9c60d734f5f63e7e"}
	{"email":"jon@aerofs.com","acting_as":":c34e3c","device":"b88a707bd70586f7a9f2dfc8fee2def5"}

##### <a name="shared_folder"></a> Shared Folder

A Shared Folder object gives the (device-local) name and SID of a shared folder. Renaming a shared folder will not impact the sid.

 - `id` : The Store ID for the shared folder

 - `name` : The name of the Shared Folder at the moment of the auditable event.


Sample JSON representation of a Shared Folder:

	{"id":"35e4a5ffe0c50f8ea5edfc85a1256fab","name":"Team Pictures"}

##### <a name="path"></a> Path

A Path object consists of the following elements:

 - `sid` : Physical root identifier

 - `relative_path` : Relative path of a file or folder within the given Store.

Sample JSON representation of a Path:

	{"sid":"e4d451120e9d291ca135d6f1f6938d9b","relative_path":"doc/api/overview.pdf"}

##### <a name="verified_submitter"></a> Verified Submitter

The Verified Submitter object will be embedded when the audit event is received over an authenticated link from a certified AeroFS client.

It will include the following elements:

 - `user_id` : Reporting user

 - `device_id` : Certified device that submitted the auditable event.

Sample JSON representation of a verified_submitter:

	{"user_id":"jon@aerofs.com","device_id":"40f9cddab98a432b9c60d734f5f63e7e"}

##### <a name="role"></a> Role

A Role array lists the privileges of a user within a Store. Possible permissions are:

 - MANAGE
 - WRITE

Sample JSON representation of a Role object:

	{"role": ["WRITE"]}


## Event Topics

The following sections describe the set of events that can be published by the AeroFS Appliance services. Some events may not apply in certain deployment configurations.



### File transfer

##### File Events

Events related to files, specifically files transferred between AeroFS clients.

 - `file.notification (verified_submitter, soid, event_time, operations, device, path, [path_to], [mobile_device])`: A local file notification.

 - `file.transfer (verified_submitter, soid, event_time, operations, device, path, [path_to], destination_device, destination_user))`: A remote file transfer event. The reporting device is the source of the event.


##### File Parameters

 - `verified_submitter` : see [type dictionary](#verified_submitter).

 - `soid`: Uniquely identify the store and object of a file or folder. See [type dictionary](#soid).

 - `event_time`: Time the actual file event took place, as reported by the client.

 - `operations`: An array of file operations. The actual file operation is the union of these operations; for instance, a single operation could include MODIFY and CREATE operations. The set of operations are:

    - CREATE (file is created)
    - MODIFY (file is modified)
    - MOVE (file is renamed or moved)
    - DELETE (file is deleted)
    - META_REQUEST (request for version/meta info)
    - CONTENT_REQUEST (request to start copying file content)
    - CONTENT_COMPLETED (file transfer completed)

 - `device` : The [device](#did) reporting the event.

 - `destination_device`: The [destination device](#did), if applicable.

 - `destination_user`: The user who owns the destination device, if applicable.

 - `path`, `path_to`: See [type dictionary](#path).


### User management

Events related to the creation, management, and roles of users.

##### User Events

 - `user.signin (user, authority)` : User signed in to AeroFS via the web or desktop client.

 - `device.mobile.error (user)` : User tried to certify a mobile device but encountered a permission or credential error.

 - `user.password.change (user)` : User changed their AeroFS password. Note that this is only supported for users with local credentials - OpenID and LDAP-authenticated users cannot use this function.

 - `user.org.invite (inviter, invitee, organization)` : User sent an invite to the given organization to an email that does not currently have an AeroFS account.

 - `user.org.signup (user, first_name, last_name, organization, is_admin)` : User signed up after receiving an invite code.

 - `user.create (email, caller)` : Admin created a user account via the User Management API.

 - `user.delete (email, delete)` : Admin deleted a user account via the User Management API.

 - `user.password.error (user)` : A user signin action was denied due to a bad credential.

 - `user.password.reset.request (user, caller)` : A user requested a password reset token to their email. This cannot occur for users that are associated with an OpenID or LDAP authority.

 - `user.password.reset (user)` : A user reset their own password using the email password reset token. This cannot occur for users that are associated with an OpenID or LDAP authority.

 - `user.password.revoke (email, caller)` : Admin revoked a user credential via the User Management API.

 - `user.org.authorization (admin_user, target_user, new_level)` : User changed organizational permission level; either promoted to an admin or demoted to a regular user.

 - `user.password.update (email, caller)` : User or administrator updated a user credential via the User Management API.

 - `user.update (email, caller)` : User or administrator updated a user name via the User Management API.

 - `user.org.provision (user, authority)` : Auto-provisioned user signed in to AeroFS for the first time. `authority` is the type of the identity-authenticating authority.

 - `user.org.accept (user, previous_org, new_org)` : User accepted an invitation to an organization.

 - `user.org.remove (admin_user, target_user, organization)` : The admin user has remove the target user from the given organization.

 - `user.account.request (email)` : A user requested an AeroFS account. NOTE: the AeroFS appliance may be configured to disallow open signup (invite-only); in that case this event will not occur.

 - `user.quota.warning (user, bytes_used, bytes_allowed)` : A user has reached 80% of their disk usage quota as reported by a teamserver.

 - `user.2fa.enable (user)` : User enabled two-factor authentication on their account.

 - `user.2fa.disable (user, caller)` : User or administrator disabled two-factor authentication on their account.


##### User Parameters

 - `authority` : Authority type associated with a user signin action. The authority may be `credential` (local password), `LDAP`, or `OpenID`.

 - `caller` : The [verified caller information](#caller) for the user that initiated and authorized the user action.

 - `user`, `admin_user`, `target_user` : UserID (generally an email address) of an existing AeroFS user. User ID's starting with a colon are special pseudo-users associated with Team Servers.

 - `email` : Email address of a person in the invitation process but without a current AeroFS account.

 - `first_name`, `last_name` : User name details.

 - `previous_org`, `new_org`, `organization` : Organization ID, as used in different contexts.

 - `inviter` : UserID of the person sending an AeroFS invite.

 - `invitee` : Email address of a person receiving an AeroFS invite.

 - `new_level` : An organizational auth level - either `USER` or `ADMIN`.

 - `bytes_used` : The size of the user's AeroFS folder in bytes, as measured by a TeamServer

 - `bytes_allowed` : The allowed size of the user's AeroFS folder in bytes, as set by the org admin



### Sharing

Events related to folder sharing in AeroFS.

##### Sharing Events

 - `folder.create (folder, caller)` : User creates a shared folder.

 - `folder.destroy (folder, caller)` : User destroys a shared folder.

 - `folder.join (folder, target, caller, role, [join_as])` : User accepts a share invitation.

 - `folder.leave (folder, caller, target)` : User leaves shared folder.

 - `folder.invite (folder, sharer, target, role)` : User sends a share invitation to an internal or external user.

 - `folder.delete_invitation (folder, caller, target)` : User deletes an invitation to a shared folder.

 - `folder.permission.delete (folder, caller, target)` : User is removed from a shared folder permission list.

 - `folder.permission.update (admin_user, target, old_role, new_role, folder)` : Shared-folder owner changes a User's role for a folder.



##### Sharing Parameters

 - `folder` : A [Shared Folder](#shared_folder) object.

 - `target` : UserID of a person invited to a shared folder.

 - `caller` : A [Caller](#caller) object that indicates the person sending an invite to a shared folder or making a change to shared folder. Note this may include a mobile [Device ID](#did) if the request is made via the Content API.

 - `role`, `new_role`, `old_role` : A [Role](#role) object for a Shared Folder invitation.


### Device Management

Events related to device certification and decomissioning.


##### Device Events

 - `device.signin` (user, device_id, ip) : A device successfully signs into the AeroFS appliance using a trusted certificate signed by the AeroFS certificate authority.

 - `device.certify (user, device_type, device_id, [device_name], [os_family], [os_name])` : A new device successfully certified with AeroFS. Some fields may not be populated for mobile devices.

 - `device.mobile.code (user, timeout)` : User generated a one-time access code for authenticating a mobile device.

 - `device.mobile.authenticate (user, device)` : User authenticates a mobile device using a one-time access code.

 - `device.recertify (user, device, device_type)` : Device replaced an existing certificate with a newer one.

 - `device.unlink (admin_user, device, owner)` : Device (owned by `owner`) is unlinked per request from user `admin_user`. Unlink means the device will keep the files it has, but it will no longer send or receive file updates.

 - `device.erase (admin_user, device, owner)` : Device (owned by `owner`) is remote-erased per request from user `admin_user`. The contents of the AeroFS folder on the specified device will be wiped.


##### Device Parameters

 - `user`, `owner` : UserID that certified the reporting device.

 - `admin_user` : UserID of the user that requested a device action. May be the device owner or an organization administrator.

 - `device`, `device_id` : [Device ID](#did) of the reporting device.

 - `device_name` : User-visible name of the device (typically the computer or mobile device name)

 - `device_type`: The type of AeroFS device reporting. This will be one of `Desktop Client`, `Team Server`, or `Mobile App`.

 - `os_family` : Textual OS family name (`Windows`, `MacOS`, `Linux`)

 - `os_name` : Specific name of the reporting OS.

 - `timeout` : Timeout in seconds of a mobile access code.

### Link-Based Sharing Events

Events related to link-based sharing of files and folders.

##### Link-Based Sharing Events

 - `link.create (ip, caller, key, soid)` : User created a link to a file or folder

 - `link.delete (ip, caller, key)` : User deleted a link to a file or folder

 - `link.access (ip, key)` : User used the link to access the object

 - `link.set_password (ip, caller, key)` : User set a password for the link

 - `link.remove_password (ip, caller, key)` : User removed the password for the link

 - `link.set_expiry (ip, caller, key, expiry)` : User set an expiration time for the link

 - `link.remove_expiry (ip, caller, key)` : User removed the expiration time for the link

##### Link-Based Sharing Parameters

 - `caller` : UserID of the user who performed the action

 - `key` : Unique string which forms the URL on which the action is being performed

 - `soid` : Unique [SOID](#soid) of the object to which the link refers

 - `expiry` : Number of seconds for which the link will be valid

### Organization Events

Events related to events that affect an organization

#### Organization Events

  - `org.2fa.level (org, caller, old_level, new_level)`: Admin changed target org's two-factor authentication enforcement level.

#### Organization Parameters

 - `caller`: UserID of the administrator who performed the action

 - `org`: The pseudo-UserID of the Team Server of the target organization

 - `old_level`, `new_level`: The level of two-factor authentication enforcement required for this organization.  This will be one of `DISALLOWED`, `OPT_IN`, or `MANDATORY`.
