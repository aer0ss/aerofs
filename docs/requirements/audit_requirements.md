# Data Leak Prevention - Auditable Events

    Current revision: 0.4
    Revision history:
      0.1 Initial draft
      0.2 Clarify channel configuration, offline client access.
      0.3 Make corrections to latency, offline access requirements.
      0.4 Update timer resolution, sharing event types.


## Purpose

System administrators should be able to determine usage and sharing patterns for their organization. This includes near-real-time status information as well as historical data.

The purpose of the audit feature in the user's organization are:

 - enable insights into how data is shared and accessed;

 - provide near-real-time monitoring of the flow of data through the organization, so the administrator can detect and prevent data leakage;

 - help retroactively identify the root causes of data leakage.

We will provide these features by publishing auditable events to existing IT infrastructure for event management.



## System Configuration


AeroFS configuration data is stored securely on the AerofS Appliance. All AeroFS clients (mobile, desktop, and web) read configuration data from the AeroFS Appliance. The configuration values are required in order to perform initial certification.

After enabling auditable event configuration, clients will pick up the new configuration the next time they restart.

Note that enabling client file-transfer auditing will increase the overall audit volume - resulting in increased network traffic.

##### Requirements

*REQ:*
An AeroFS site administrator can enable, disable, and configure the Data Leakage Prevention features from the admin web interface. These actions will require putting the appliance instance into Maintenance Mode briefly.

*REQ:*
Clients cannot certify (and use AeroFS features) without reading configuration state from the server.

*REQ:*
The following features are individually enabled and configured by the site administrator:

 - Audit Service;

 - client file-transfer auditing;

 - delivery of audit events to downstream systems.

See below for each of these.



##### Configuration summary:

The following elements are admin-configurable. If "data leakage prevention audit logging" is disabled, the remaining options are ignored.

 - data leakage prevention audit logging { Enabled, Disabled }

 - client file-transfer audit logging { Enabled, Disabled }

 - Audit Service configuration { See following section }


## AeroFS Audit Service

The AeroFS Audit Service enables all the other features described in this document. It is the central point that accepts auditable events from various sources, applies formatting as needed, and delivers the events to one or more downstream systems.

The audit service is not required to provide indefinite local buffering of auditable events. This service assumes that downstream systems are reliable.

##### Requirements

*REQ:*
The Audit Service accepts auditable events from server processes on the AeroFS Appliance.

*REQ:*
The Audit Service accepts auditable events from remote devices via a secure endpoint.

*REQ:*
Audit activity from remote devices must be authenticated and encrypted.


##### Configuration summary:

The following elements are admin-configurable:

 - audit service state { Enabled, Disabled }

 - downstream systems { sorted list of channel configuration blocks; see below. }


## Client file-transfer auditing

AeroFS clients record file transfer events. These events include, minimally, the following data:

 - file metadata (file path and name, modification time, and size);

 - file transfer type (new file, deletion, update);

 - file version identifier (opaque but used for event correlation)

 - remote endpoint - this is an AeroFS device id.


##### Requirements

*Note:* We call an AeroFS client "offline" if it is unable to reach the AeroFS Appliance; for instance, in a segregated external network. An offline client may still perform local-network file transfers with certified peers.

*REQ:*
If client file-transfer auditing is enabled, AeroFS clients will upload the file transfer events to an audit collection service on the AeroFS Appliance.

*REQ:*
If client file-transfer auditing is enabled on the appliance, clients cannot locally opt-out.

*REQ:*
Activity logs of local-network file transfer events will be batch-uploaded to the AeroFS Appliance when the client is next online.

## Channels: Delivery to downstream Systems

Auditable events must be delivered to a downstream event capture and analysis system. The site administrator will choose the destination system or systems.

The channel encapsulates configuration of the transport, network address, and message protocol to deliver.


##### Requirements

*REQ:*
One or more destination channels can be configured in the web interface.


##### Configuration summary

For each downstream connector, the following will be configured:

 - Name { for internal tracing }

 - Channel { `json-stream` }

 - Channel-specific configuration, if any { none supported for json-stream }


### JSON-Stream Channel

This is a simple channel that streams JSON documents sequentially to a downstream system.

This channel can be used to connect to a Splunk server on a network listener port.

##### Requirements

*REQ:*
The JSON-stream channel is capable of delivering events directly to a Splunk (or similar) server.

*REQ:*
If configured for tcp-ssl, this channel requires correct certificate information.

##### Configuration summary

The following are configured for each JSON stream instance:

 - Device URI { supported transports include `tcp://` and `tcp-ssl://` for simple tcp streams }

 - Certificate information (used if the remote service does not have a publicly-signed cert)



### Elasticsearch Channel

This is a simple channel that publishes events to a named elasticsearch index.

##### Requirements

*REQ:*
The elasticsearch channel is capable of delivering events to an existing elasticsearch cluster.

##### Configuration

In addition to the Channel configuration described above, the following additional components may be configured for this channel type:

 - cluster URI { supported transports include `http://` and `https://` }

 - Certificate information (used if the cluster does not have a publicly-signed cert)

 - elasticsearch index name { string supporting some substitution markers including date; e.x. "aerofs-events-%D" }



## Auditable Events

Regardless of source, auditable events are internally handled as lightly-structured key-value documents. They will be converted to an appropriate representation (JSON for example) by the output channel types.

*REQ*:
Although no one schema is used for all audit events, every event must include the following:

 - timestamp: millisecond-resolution time and date stamp

 - topic: one of { file, user, sharing, device }

 - reporting component: device id or service name



## Representative Event Topics

The following sections give representative examples of the events to be published by the AeroFS Appliance services.

##### File transfer

Events related to files, including files transferred between AeroFS clients

 - file transmitted to device (source device, destination device, file metadata, transport used, version identifier)

 - version promoted to current (file metadata, version identifier)


##### User management

Events related to the creation, management, and roles of users.

 - `user.account.request (email)` : Request a signup code for email address.

 - `user.org.invite (inviter, invitee, organization)` : User sends an AeroFS invitation to an email address.

 - `user.org.accept (user, previous_org, new_org)` : User accepts an organization invite

 - `user.org.remove (admin_user, target_user, organization)` : User is removed from an organization (not currently possible in private cloud).
 	
 - `user.org.signup (user, first_name, last_name, organization)` : User signs up from invitation.

 - `user.org.provision (user, authority)` : Auto-provisioned user signed in to AeroFS for the first time. `authority` is the type of the identity-authenticating authority.

 - `user.org.permissions (admin_user, target_user, new_level)` : User permission level changed (promoted to organization admin or demoted to normal user).

 - `user.password.reset.request (user)` : User requests password reset email.

 - `user.password.reset (user)` : User resets password.

 - `user.password.change (user)` : User changes password.

 - `user.signin (user, authority)` : User signed in to the AeroFS Appliance. `authority` indicates which identity-authentication type was used; it will be one of "Credential", "LDAP", or "OpenId" depending on the appliance configuration and user account type.

 - `user.password.error (user)` : Received invalid credential information


##### Sharing

Events related to folder sharing in AeroFS.

 - `folder.invite (folder, sharer, target, role)` : User sends a share invitation to an internal or external user

 - `folder.join (folder, user, user_type {external | internal})` : User accepts a share invitation

 - `folder.permission.update (admin_user, target_user, old_role, new_role, folder)` : Shared-folder owner changes a User's role for a folder

 - `folder.leave (user, folder)` : User leaves shared folder

 - `folder.permission.delete (admin_user, target_user, folder)` : User is removed from a shared folder (Admin, User, Share, Role)


##### Device Management

Events related to device certification and decomissioning.

 - `device.mobile.code (user, timeout)` : Request a one-time access code for authenticating a mobile device.

 - `device.mobile.authenticate (user, device)` : User authenticates a mobile device using a one-time access code.

 - `device.certify (user, device_id, device_type, os_family, os_name, device_name)` : A new device successfully certified with AeroFS. Device certification requires User privilege, and will be associated with that User. `device_type` will be one of "Desktop Client", "Team Server", or "Mobile App". Some fields may not be populated for mobile devices.

 - `device.recertify (user, device, device_type)` : Device recertification. Device_type is "Team Server" or "Desktop"

 - `device.unlink (admin_user, device, owner)` : Device "device" owned by "owner" is unlinked by user "admin_user".

 - `device.erase (admin_user, device, owner)` : Device "device" owned by "owner" is remote-erased by user "admin_user".

 - `device.mobile.error (user)` : Tried to authorize a mobile device with an invalid device authorization code.
