Storage Agent definition (v0.3)
==
    v0.1 2015/01/08 jP initial draft, functionality only
    v0.2 2015/01/13 jP updates from review
    v0.3 2015/01/20 jP updates from Matt's review

The Storage Agent is a client application that syncs files in an AeroFS private
cloud deployment. Wherever possible, it leverages existing mechanisms for
syncing, metadata, and transport. The Storage Agent provides additional
features to end-users, and increases the usability and utility of the AeroFS
Private Cloud.

The Storage Agent, aside from providing a "known reliable" permanent file
storage solution, can also act as a network repeater to improve reliability and
performance of AeroFS. Files can be geographically dispersed across many nodes;
each one can service local clients that may not be able to reach peer devices.

This document is intended to provide scoping and requirements at a product
level.


Functional
==

 -  Phoenix-Only: The Storage Agent receives metadata updates only from
    Polaris.


Authorization
==

Consider these requirements in two areas:

 - certify a device: authenticate a user, and generate a local durable
   proof-of-identity.
 - authorize a session: use a stored proof-of-identity to authorize a session
   with peers and services.

The mechanism that configures a Storage Agent is not required to be the same
client binary as the Storage Agent itself. Specifically, the certification
(setup) tool will be interactive - some information must be requested from the
end-user. However, the Storage Agent itself should be able to run as a system
service, without an attached console.

This may simplify implementation - the Storage Agent can assume the existence
of a local .properties-configured file, that provides all required local
authentication and configuration, without worrying about how or when those
values are captured.


Certify a device
====

 - Only an administrator-privileged principal is permitted to certify a Storage
   Agent.

 - Once identity has been established by exchanging appropriate user
   credentials, the client submits a certificate to the identity authority for
   signing. The certificate is kept locally to exchange as proof-of-identity for
   the device from that point forward.

 - The Storage Agent certification process must support all authentication
   methods offered for AeroFS clients, including Two Factor Authentication.

 - The Private Cloud Appliance must refuse session authorization if the Storage
   Agent feature is not enabled by the appliance license.


Authorize a session
====

 - Polaris (and any other relevant servers) must distinguish Storage Agent
   nodes from other client types, including regular clients authorized by
   organization admins.

 - An authorized Storage Agent must possess a local proof-of-identity to
   exchange with services on the Private Cloud Appliance.

 - An authorized Storage Agent must possess a local proof-of-identity to
   exchange with AeroFS clients. These proofs may be the same.

 - The Storage Agent must be able to use an existing proof-of-identity to
   generate a new local certificate. It will do so when the existing
   certificate is within 3 months of expiring.


Content Update
===

A content transaction consists of:

 - learning of a content update from Polaris;
 - record the existence of the { store, content } tuple.
 - if the content object does not exist locally, append the content object to
   the work list.

The "work list" is meant to be conceptual, not implementation advice.
Processing content objects from the work list involves:

 - check for local existence of the content object. If it exists, mark the
   content object as present and exit.

 - check for an update to the object that supersedes this content object. If
   such an update exists, mark the content as not present and exit.

 - attempt to download the content from any device that has reported existence
   of that object.

Content requirements are as follows.

 - Content objects are identified by a unique identifier - a suitably-secure
   hash of the contents.

 - An authorized Storage Agent connects to Polaris and subscribes to metadata
   notifications. It can safely ignore any metadata update that does not
   indicate new content.

 - The Storage Agent will attempt to download any content that it does not
   already have. This download can use any device-to-device transport
   appropriate for the reporting device.

 - For each insert of a given content object, the Storage Agent records the
   existence of the { Store, Content } association.

 - If a given content object is already present on the Storage Agent, even in a
   different Store, the agent may not have to download the content at all.
   (todo: make sure we like this policy; it is a nice optimization but it tells a
   user if a given object exists in someone else's stores)

 - The Storage Agent is not required to collect every version of every Content
   object. If an object is superseded - i.e. another update to the same object
   arrives - before a given content can be obtained, it may be ignored.

  (is this clear, or need an example here?)

 - If an organization operates multiple Storage Agents, each one will attempt
   to gather the updates. The Storage Agents should sync among themselves. Some
   heuristics to avoid having a client machine receive N simultaneous content
   requests from Storage Agents would be nice here.


Content Aging
====

 - The Storage Agent keeps revisions forever.

In the future, we may consider a policy in which the Storage Agent can prune
revisions, as long as a newer revision of the same content object is already
stored locally. The first step to supporting this is the ability to prune
(delete) a specific revision of a specific object.


Access Control
===

 - The Storage Agent must authenticate every request for content. The calling
   device must possess a valid permission for the given content, as reported by
   the Private Cloud Appliance. A "valid permission for the given content" means
   that the device belongs to a user that is a member of the Store.

 - The Storage Agent can call out to a service on the Private Cloud Appliance
   to validate { user, store } permissions before delivering content. The
   results of these calls may be cached to reduce the amount of network traffic
   required per content transaction. Changes to ACLs should be propagated to
   Storage Agents with reasonably low latency. #FIXME: specify allowed lag exactly

^

*NOTE*: Content requests must specify the Store and the Content Hash. The
Storage Agent needs to keep track of each chunk of content stored;  { store,
content } is a unique key. Then each request needs to check two simple things:
    - does { store, content } make sense?
    - does { store, user } make sense?


Storage
===

 - The Storage Agent must support using a local filesystem as the storage
   mechanism. #FIXME: any other specifications? NFC?

 - "Linked" filesystem storage is not required - in other words, the layout on
   disk does not have to include recognizably-named files.

 - The Storage Agent implicitly provides deduplication.

 - FUTURE: The Storage Agent may be configured to encrypt local file contents.

 - FUTURE: The Storage Agent may be configured to compress local file contents.

 - FUTURE: The Storage Agent may be configured to use an S3-compatible service
   for storing files.

 - FUTURE: The Storage Agent may be configured to use an SWIFT-compatible service
   for storing files.


Persistence, Replication, and High Availability
===

We assume the disk is sufficiently durable to provide persistence of data that
is received by the Storage Agent.

 - The content metadata must be persistent. The directory of received content,
   and the mapping of content to Stores, must survive system shutdown and
   potentially ungraceful exit.

 - An organization may start multiple storage agents to provide geographic
   redundancy and a disaster recovery plan.

 - There is no requirement to provide sharding or minimum replication size for
   this release.

 - Nice to have: a documented way to make a backup of the Storage Agent content
   and local metadata.


Transport
===

The Storage Agent supports the following transport types:

 - TCP-LAN: direct connect to peers found via local multicast.
 - TCP-WAN: direct connect to peers found via centralized IP registry.
 - Relay: connect to the relay service, coordinating via XMPP.

NOTE: To request content from a device, the Storage Agent needs to initiate the
connection? Or do we expect all 'connected' nodes to talk to a Storage Agent?
:(


TCP-WAN transport type
====

A new transport type is proposed, TCP-over-WAN. In this transport, a
centralized service is used to provide presence and address information to
devices. The devices can then connect directly using TCP - this may require a
dedicated listener port and firewall updates on the recipient.

 - For the initial release, only Storage Agents are required to know a public
   address that they can register with the discovery service.

 - Clients can get the address information for connected Storage AGents from a
   known location on the Private Cloud Appliance.

 - Storage Agents are required to 'know' a public address and port, and to
   provide that information to the discovery service.


Deployment and Monitoring
==

Requirements related to the deployment and distribution model, as well as
administrative oversight.

Discovery
===

 - The Private Cloud Appliance must provide a discovery mechanism for Storage
   Agents. Storage Agents register with the discovery service at startup;
   desktop clients can then request the list of expected Storage Agent devices in
   the network.

 - Administrators will be able to view the set of expected (registered) Storage
   Agents in the web view for their deployment.

 - Associating the Storage Agent identifiers with network locations and
   transports is left to the TCP-WAN mechanism.

 - The TCP-WAN transport includes a centralized "device discovery" service.

 - Authorized clients can request a list of Storage Agent nodes from the
   discovery service on the Private Cloud Appliance.


Configuration
===

The Storage Agent requires an interactive configuration tool. This tool is responsible for:

 - obtaining all private-cloud related configuration (appliance address);
 - asking the user to verify the certificate received from the given host;
 - certifying the Storage Agent;
 - obtaining host-specific configuration as needed:
     - location on disk to use as storage
     - database configuration (?)

The interactive configuration may be a web-based interface, or a command line
tool; this depends a bit on the deployment model, as described below.


Internals
===

This section describes the deployment requirements for the actual Storage Agent
binary. If the Storage Agent is distributed as a container/appliance, then
these will be hidden from the end user.

 - The Storage Agent itself is only supported on 64-bit Linux systems. (This is
   academic given the appliance distribution model.)

 - The SA requires a properties file to function. Attempting to start the SA
   without a valid properties file (in the approot) will result in an
   immediate, non-zero, exit.

 - The properties file provides all local configuration required for the agent
   binary, including at minimum:
    - appliance host : hostname or address of the Private Cloud Appliance.
    - polaris cert : Optional; the expected public certificate (or the signing
      authority thereof) for the appliance service port.
    - content root : Storage area on the local filesystem.


Monitoring and Upgrades
===

 - The Private Cloud system status page will report the status of configured
   Storage Agent nodes.

 - The Storage Agent does not auto-upgrade. *Note: This implies we will have to
   communicate carefully when an appliance upgrade would break compatibility
   with deployed Storage Agents.*

 - Storage Agent upgrades occur in-place. The upgrade should support direct
   download from aerofs.com as well as direct provisioning from a local
   administrator.

 - A signed Storage Agent should validate that any upgrade is also properly
   signed by AeroFS.

*TODO*: Provide administrators with better information when an upgrade is
available (the Storage Agent lags the appliance).


Distribution
==

For beta users, an application distribution may be sufficient; however the
container/appliance model is assumed to be superior user experience for most
administrators. This is the only supported distribution model for the production
storage agent.

The Storage Agent is not required to be bundled in the Private Cloud Appliance
- this is a difference from the existing Team Server.

 - A link to the Storage Agent download SA installer is bundled in the AeroFS
   Private Cloud Appliance image. Administrators will be presented with a
   Storage Agent download link in the appliance web views.

 - If the appliance is not licensed for the Storage Agent, an
   informational/marketing/upgrade page will be presented in place of the
   download link.


Appliance / container distribution
===

 - The Storage Agent may be distributed as a virtual appliance (or container?).

This requires some upfront work for presenting an authenticated web
configuration tool. However, it allows us to ship an onboard database and
reduce the configuration thereof. It may also reduce our support effort for the
Storage Agent.


Futures - Out of scope
==

This section exists to list the things that we know will have to be included
some day, but are currently *out of scope*.

 - Sync status: The policy _will_ be, "once uploaded to a Storage Agent, a file
   can be marked as securely copied". But we are not building that
   infrastructure on the first release. (This is mostly for Polaris and the
   desktop client)

 - Integration: Integration with SharePoint, Linked filesystem storage type
   (for sharing via CIFS or similar).

 - Replication factor / sharding: Instead of copying all content to all STorage
   Agents, we should support a smarter approach of "at least N copies of any
   object".

 - Geographic redundancy: This is achieved in the first release with full
   replication of all contents.

 - Network optimizations and tuning: Heuristics for whether to prefer
   Storage-Agent-to-Storage-Agent copies, prefer upload or download traffic,
   etc., will all be evaluated on running systems rather than designed up front.

