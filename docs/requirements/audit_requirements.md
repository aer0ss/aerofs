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

 - User sends an AeroFS invitation to an email address.

 - User signs up for AeroFS account.

 - User promoted to organization admin, or demoted to normal user.

 - User requests password reset email.

 - User resets password.

 - Web signin (User, source address)

 - Received invalid credential information


##### Sharing

Events related to folder sharing in AeroFS.

 - User sends a share invitation to an internal or external user (Inviter, Invitee, Share, Role)

 - User accepts a share invitation (User, Share, Inviter, Role)

 - Shared-folder owner changes a User's role for a folder  (Admin, Target, Share, Old Role, New Role)

 - User leaves/removed from a shared folder (Admin, User, Share, Role)


##### Device Management

Events related to device certification

 - Request access code for mobile device (User, Mobile device id)

 - Mobile device authentication occurs (User, Mobile device id)

 - New device certification (Device ID, User)

 - Device recertification (Device ID, User)

 - Device is unlinked (Admin, Owner, Device ID)

 - Device is remote-erased by an admin (Admin, Owner, Device ID)

 - Received invalid certificate information from a device.
