# Centralized Metadata

(open questions listed at bottom)

Engineering requirements and design notes on a proposed change to the core version and
content syncing behavior.

## Product Requirements

#### User

 - I want to know if I sent my version.

 - I want to know if I have the latest version.

 - I want to access my files anywhere anytime.

 - I want to recover the latest version of my file.

 - I want to recover previously versions of files.

 - I want to exclude some of my files from the storage service. (admin permission)


#### Admin

 - I want to scan the metadata and content my users sync.

 - I want to recover all the files of my organization.

 - I want to set up quotas to users.

 - I want to assign users to a specific storage service. (for locality, confidentiality).

 - I want to track all user activities, including the admin themselves.

 - I want to discover if data leaked outside the organization.

 - I want to reassign the folders from a user to another one.


## Behavioral and feature changes

The user-visible changes to the behavior of AeroFS in the new world are:

 - reliable sync/storage feedback to users: notification that your update has been
   accepted and will be propagated if you go offline now.

 - conflicts are local only. Only the device that made a change concurrent with someone
   else will see the conflict dialog. The conflicting change will not be synced with peers
   until dealt with explicitly by the author.

 - delivery of content to peers can be gated by a file content scan from an ICAP system.

 - all audit traffic is centralized: every update and successful sync is recorded by a
   central managed server.

 - API requests for content will return consistent results for a given object.

 - centralized undelete (and resurrection of old versions) can be supported.


## Functional Specification, stories of the new world

### 1. A wild update occurs!

A user creates a new file, "Cats are nice.doc" on their local device. The AeroFS client
notices the new file, assigns it an OID, and makes a call to the Metadata Server:

	"I, device {d0} have object {o0} with metadata {m0} and content {c0}."

The metadata server checks that this is not in conflict, assigns a tick, and returns that
value to the caller. "Welcome. Your tick is t0".

The metadata server notifies all other parties interested in that store about the new tick.


### 2. Syncing is caring.

Other devices that belong to the user's store are told that a new tick is available for a
particular object. The notification is something like:

	"Hey, device d0 has tick t0 for object o0, FYI."

The recipients of this message reach out to d0 to request the content using existing
Transport infrastructure.


### 3. Reliable storage is introduced.

A Storage Agent is added to the world. The Storage Agent subscribes to all the stores, and
so it receives the same object-update notification the other devices do.

The Storage Agent also requests the content for tick t0 from device d0. (In a perfect
world, d0 will heuristically prefer to speak to the Storage Agent).

When the content is fully received and stored in a reliable way, the Storage Agent
notifies the VS:

	"I'm device d1 and I also have tick t0 for object o0"

The VS knows that d1 is an "authoritative source" so it internally breathes a sigh of
relief. Device d0 and others are notified of this availability update.

The AeroFS UI on device d0 can indicate to the user that the update has been reliably
saved, and they can safely go offline without causing problems.


### 4. People cause conflict.

"Cats are nice.doc" becomes hugely influential and many people share it.

Two devices, d2 and d3, have tick "t3" for this object. They simultaneously modify the
file, each adding a poem or drawing about cats (and how nice they are).

d2 comes online first, and it tells the Metadata Server that it has an update to this
object.

	"I'm device d2 and I updated object o0 starting with tick t3, my new metadata is m4".

The Metadata Server responds with tick t4.

When device d3 comes online, it tells the Metadata Server almost the same thing:

	"I'm device d3 and I updated object o0 starting with tick t3, my new content hash and
	metadata is {...}".

The Metadata Server isn't mad, just disappointed. He responds with:

	"Son, you are in conflict. The current tick is t4 with content hash and metadata {..}"

Other users are never notified of d3's unfortunate indiscretion. As far as the VS and the
network is concerned, it never happened.

On d3, the UI displays a conflict notification. The device may (should) choose to download
t4 and ask the user to reconcile the two versions of the file.  (Save two copies, squash
one, manually merge... UX goes here)

When conflict resolution is completed, the device can come back to the Metadata Server:

	"I'm device d3 and I updated o0 starting with tick t4, here's the details..."


### 5. Undelete yourself

**TODO**: describe undeleting a file from the web interface. This is partly fleshed out
in the design; describe it from a user POV.


### 6. Freedom of movement

**TODO**: describe moving an object to a different store. This is partly fleshed out in
 the design; describe it from a user POV.


# Components of the plan

We propose two new components, and changes to some existing components (core, transport,
and the API)

 - (new) Metadata Server: coordinate metadata between peers and storage agents

 - (new) Storage Agent: an authoritative data store, providing reassurance to a user that
their updates are stored reliably.

 - (new) transport: TCP-WAN

 - (modifications) core: changes to the internals as well as to the protocol to support a
centralized metadata service. Some simplifications become available once we have a
reliable metadata server.

 - (modifications) API: a shim will be required to execute API requests against the new
Metadata Server and Storage Agent.


## Component: the Metadata Server

The Metadata Server is a single point within the network that coordinates updates to every
object in AeroFS. It provides a linear version history for each object.

*Definitions:*

 - A version is a scalar value which is monotonically increasing on a per-OID basis.

 - An object has a single latest version on the metadata server. Conflicts can exist on a
local machine, but must be resolved before pushing to the metadata server. The version
server does not have the concept of conflict branches.


### Requirements, functional

Coordinates all updates to objects across all devices within an organization.

Provides a linear history for each object, which implies that it can easily determine what
is the latest version for a given object.

Explicitly informs a client that a proposed update to an object would cause a conflict,
and prevents that conflict.

Allows clients to explicitly override such a conflict, squashing an update known to the
Metadata Server.

Notifies interested clients when an update occurs to any object inside a Store. When no
such updates exist, a client can be notified that it has all updates from the network and
can safely go offline.

Tracks which devices are known to host the content of any (see below) version of any
Object.

Notifies clients when a given version of an object is available on an authoritative
source. This allows clients to provide explicit notification when their local updates are
stored on authoritative backing stores, and so can safely go offline.

Must prevent unauthorized devices from submitting changes to objects within a Store, and
prevent unauthorized devices from requesting state information about a Store.

Always able to provide a list of Devices that are hosting the newest version of an Object.

This Server is permitted to collapse old version information (**TODO**: based on what
mechanism?). In particular, a client should not assume it can ask which Devices are
hosting an ancient version of an object. NOTE: this effectively becomes the threshold of
'version history'.


### Requirements, non-functional

Note that clients that cannot talk to the Metadata server are offline, and cannot sync
with local peers. This implies that the metadata server has high availability requirements.

Performance - an update occurs for every update to every object in the organization.
Scaling this (particularly to the Hybrid cloud) could be a big job.

In private cloud, the ability to reuse existing database infrastructure is desirable. The
contents of the Metadata database are precious - losing them will have a huge impact. As
this may be a lot of data, we need to carefully consider the Private Cloud upgrade
mechanism.

### Design Notes

Note that the Metadata Server is operationally similar to the new, long-poll verkehr.
Clients want to wait for notification that updates have occurred on one of their
authorized Stores. Clients submit actions (object was updated, I now have content, etc.),
and these actions cause notifications to go out to interested clients.

### API

Described here as RESTful for convenience. This is conceptual and doesn't necessarily
indicate a firm protocol decision.

#### Common Data Structures

	Version: Int  // inc. per OID

	Epoch: Int  // inc. per store

	Metadata:
		oid      OID
		version  Version
		parent   OID
		name     String
		store    SID
		type     {FILE, FOLDER, ANCHOR}
		content_hash  Hash
		content_size  Int

#### Objects

	Update:
		oid      OID
		newest   Version
		devices  List<DID>


#### Methods

##### 1. "What has changed in {store} since this time?"

This should use an interruptible long-polling mechanism.

	Request:
		GET /stores/{sid}/updates
			since  Epoch

	Response:
		epoch    Epoch
		updates  List<Update>

##### 2. "Who has the content for {object} at {version}?"

This should be an immediate return.

	Request:
		GET /objects/{OID}/{Version}/devices

	Response:
		devices  List<DID>
		latest  Boolean (true if {Version} is the latest version for {OID})

##### 3. "I have changed {object}, starting from {version}"

optionally: "I know {object} at {version} exists out there, but the guy who has it has
gone AWOL, so please make this the new canonical copy"

	Request:
		POST /objects/{OID}
			base    Version incl. Metadata sans version
			force  Boolean (optional, default: false)
			greatest_known  Version (required iff force = true)

	Response:
		oid      OID
		version  Version

	Errors:
		Conflict: (the latest version is not {base} AND force = false) OR (the
latest_version is not {greatest_known} AND force = true)


##### 4. "I have this object at this version"

Synchronous notification from devices and Storage Agents.

	Request:
		POST /objects/{OID}/{Version}/devices
			DID

	Response:
		{empty}


## Component: the Storage Agent

The Storage Agent (SA) is, in many ways, a simplification of the existing Team Server. The
SA requests content from devices, stores that content, and delivers it upon authorized
request.

The SA does not require any user interface after the initial configuration is complete.

Once the content for a given version update is accepted by a Storage Agent, the user can
be confident that their node can go offline and the content will be available to others.
The Storage Agent should be considered a reliable mechanism that will
propagate the content to other Agents as well as to peers and API users.

The Storage Agent should be architected to allow for multiple instances (for load sharing
as well as for locality). We may make simplifying assumptions for the initial release,
including supporting only one agent for initial releases.


### Requirements, functional

The SA receives information about all object updates within the organization.

Version update notifications include a content hash. If that content is not already known
to the SA, it attempts to retrieve the content from a device that currently has it.

When the content is successfully retrieved and stored, the SA notifies the Metadata Server
that the object is available on an authoritative store.

Writes content updates through to a persistent backing store. S3 and disk storage are both
supported.

Delivers content on request to authorized remote devices over any existing Transport
mechanism.

Provides a dedicated transport for external access. See the Transport section below.

Provides content for requested "undelete" actions.

Integrates with ICAP.


### Not required, or future:

Not supported in hybrid cloud?

FUTURE: replicates data across multiple Storage Agent instances

FUTURE: implements content retention policy for old versions

FUTURE: scales horizontally

FUTURE: supports geographical distribution (one SA in each remote office)

### Requirements, non-functional

Easily backed-up.

An organization can function (with reduced features) without a Storage Agent.

### Design notes

#### Design notes, Content API

The Storage API is indexed by OID+version. The unit of granularity is the object, i.e. the
file or folder.

The Storage Agent speaks the existing Core protocol (with any changes required by other
parts of this design). It communicates directly with Device instances as well as with the
Metadata Server and the API server.

There are good reasons to consider supporting chunks directly in the future (like s3, BT,
etc), but somebody still has to manage the mapping of a file to a set of chunks. This is
deferred for now.

Content hashes are used only for optimization - avoid pulling content if it is already
indexed and stored on the Agent.


#### Design notes, Transport

The Storage Agent uses the same transport mechanisms that the Devices rely on; TCP-LAN,
and Zephyr.

A new Transport type (TCP-WAN) is proposed. This transport would be supported by the
Storage Agent, and with some small firewall changes, it provides a fully-DLP-secured
environment for out-of-network devices.

Getting content: content is requested from devices by object id + version identifier. The
set of devices that can be asked for the content is provided by the Metadata Server.

Delivering content: content is pulled from the Storage Agent by object id + version
identifier.


#### Design notes, TCP-WAN

The existing TCP-LAN transport provides very fast transport between two peers - however,
it depends on the IP multicast mechanism, which is generally limited to a subnet.

The TCP-WAN transport will reuse the same Unicast transport code, and provide an explicit
mapping from Device ID to IP/port address information. The Storage Agent will provide a
TCP-WAN listener on a fixed port.

Remote devices will have the ability to request content directly from the Storage Agent
over this protocol.

Administrators that wish all remote access to be gated by the DLP features may simply
block access to the relay server port from outside the protected network. The Storage
Agent will only provide DLP-scanned content.

#### Design notes, Authorization

The Storage Agent must be authorized with an administrator credential - in the same way
the current Team Server is, in order to request objects from any device in the
organization.


## Component: Changes to Core

This design discusses two styles of version propagation:

 - legacy version handling using the distributed algorithm. Currently in production.

 - centralized version management.

The changes to the core algorithm are not explicitly listed here, but overall design
approach to changes in the AeroFS daemon are discussed.

#### Core Sync Algorithm

{ Details to be filled in as much as necessary. }

When an update to a local file is detected, the new metadata structure is built and
submitted to the Metadata Server. If the update is accepted, the returned tick is recorded
locally.


#### Updates to Stores

Any given Store is either handled by the Metadata Server, or marked as handled by the
legacy distributed version algorithm.

The Stores table in the SP database must be amended to include a version indicator
(default 'legacy' for all existing Stores).

A device can convert its local representation of a Store by converting all the objects
in that Store to use centralized ticks (see Tick Conversion, below). Later the device can
flush the local legacy version information.

Distributed version updates must not be accepted in converted Stores.

**TODO**: Devices need a signal to check for required Store conversions. It would be nice
to do this in a DPUT-style task, as the DPUT must complete before we start syncing. Also
it properly handles progress updates, etc.

**TODO**: Tick conversion in Hybrid cloud should proceed one Store at a time. This lets us
 slowly ramp up traffic on the Metadata Server. Identify the rollout plan.


#### Tick Conversion

Content that has already been synced with AeroFS must transition to the new centralized
system with as little user-visible noise as possible. Specifically, we wish to avoid the
creation of spurious conflicts.

This implies that distributed version information must be provided to the Metadata Server.

The Metadata server must support an API that accepts an object id and a legacy version
vector, and returns a centralized tick for the content.

If the object id is not known to the Metadata server, a record is created for it. The
legacy version vector is recorded, along with the mapping from the last legacy tick to
the first centralized tick. The new distributed tick is returned to the caller.

If the object id is known to the Metadata Server, the server will compare the provided
legacy version to the one stored locally. If the provided version is older than or equal
to the recorded version, the current distributed tick will be returned.

If the provided version contains updates that are not known to the Metadata server, the
distributed version is in conflict. The Metadata server may return a conflict notification
along with the current centralized tick. The result for the device is the same as if a
conflicting local change was made after the conversion to a centralized version.

As a best-effort UX improvement, the Metadata server may examine the centralized version
history. If there have been no content updates to the object in the centralized version
store, the recorded legacy version may be safely updated. A new centralized tick will be
generated for the object (which will cause notifications to all devices that had the
centralized tick).


#### History

Object updates can include migration across Store boundaries. The Metadata Server must
preserve the linear update history of the object (the "happens-before" relationship is
preserved).

However, communication of the object history to clients must be filtered according to the
result of Store migration events. Conceptually, each tick applies to an object within a
Store; therefore we can return only the ticks that are valid for the caller.

Each tick is either accessible (in a Store the caller has access to) or inaccessible
(the caller does not have access to that Store). The permission of the caller at the time
of the request is considered; *not* the permission at the point when the tick was created.

The Metadata server builds the version history starting at the current state and moving
backward.

 - if the newest version is inaccessible, it is ignored. Moving backward, all contiguous
 inaccessible versions are ignored at least until one accessible version is found.

 - once an accessible version is found, all contiguous accessible versions are added to
 the version history until the first inaccessible version.

The version history, if any, is returned. The result of the above is that only versions
that would be accessible to the caller are ever returned. Versions across multiple stores
may be returned if the stores are all accessible to the caller.

	Discussion:

	Consider sharing a populated folder "F" from a user "U"'s root anchor. All of the
	objects in F must be moved to the new Store. The devices belonging to U should not
	need to resync content for any of these objects.

	Users invited to the new shared folder are not permitted to see object history that
	predates the creation of the shared folder.

	If the original user U is then removed from F, the F folder and all its children are
	deleted on all devices. However, U has a chance to examine and download the versions
	that existed prior to the creation of the shared folder. That user cannot examine
	updates that occur after the move to the shared folder.


#### Undelete and Version History

	"Do or do not, there is no undelete."

A deleted object cannot be marked undeleted. However, given storage for content whose
metadata is marked deleted, we can support a single mechanism that provides users with
undelete-like behavior as well as a centrally-managed version history.

Callers (presumably via the web although that this a UX decision to defer) will request
a list of deleted objects within a given store.

Given the list, the user can select a particular object and version. The hints provided
with each version will include the path at time of deletion, file size, and modification
time.

An API user can request an arbitrary version of an object by providing the object id and
version indicator. The request is valid if the user has access to the Store for that
version, even if the object is marked deleted.

The caller can request superceded or deleted content using the normal addressing mechanism
supported by a Storage Agent, which includes the version identifier.

( Figuring out what to do with the content is a UX decision left to better minds.
We imagine something simple like providing the content as a browser download only. )

#### Limits to File Recovery

As described above, the ability to recover previously-stored versions of files is limited
to updates in stores that the caller can access.

**TODO**: Do we want to give the Metadata Server the ability to compress or evict very old
updates? Presumably yes?

The content retention policy for updates belongs solely to the Storage Agent. This implies
that in some cases,  the Metadata Server may provide version information, but the content
will have expired.


## Component: The API coordination Shim

Because the metadata and content are handled separately, a shim is required between the
public API and the Metadata Server/Storage Agent. It handles turning a simple public API
request for content into the right combination of Metadata requests and Content requests.

The actual deployment of this shim (part of the Storage Agent? Part of the API server?) is
TBD.

The API server will prefer to talk to a Storage Agent if one exists. Similar to how the
gateway prefers a Team Server to a regular client. If no Storage Agents are online, the
existing implementation should provide content to API requesters.


### Requirements

When no Storage Agent is available, the API/Gateway are unaffected. Requests are directed
to clients using the existing mechanisms.

When a Storage Agent is available, all content requests through the API deal with the
greatest version available on a Storage Agent. Versions that have are known to the
Metadata Server but are not available on a Storage Agent yet are ignored.

Interrupted content upload through the API should not result in a version update on the
Metadata Server, as this situation will create conflicts for all other devices.


### Design

Metadata and content actions are not performed using transports/protocols that are easily
handled by an API server. Therefore, the Metadata Server and Storage Agent must both
support a small shim specifically for API requests.

Uploading content via the API server involves the following:

 - notify the Metadata Server of an update to the content;

 - provide the content to a Storage Agent.

#### Metadata Shim

Provides the following functions in a way that can be addressed by the API server:

 - update object (given previous version)

 - create file (given path)

 - get a route to the content for this object (from a device or Storage Agent).

**TODO**: figure out what form this takes

#### Content Shim

The API server will continue to use the daemon Gateway to request content directly from
devices that have checked in.

In general, the API should prefer a Storage Agent if one is online and has the requested
version. The content server will require a simple HTTP mechanism that can either be
proxied by the API, or (even better) called directly by the API client using an
authorization token.

**TODO**: clarify this mechanism. HTTP with an OAuth token?


# Execution, or, How Do We Get There From Here?

Development and deployment each present complications. However, the same approach reduces
risk in both domains.


## Deployment

Transitioning users to the new world is hard.

A knife-switch on the new release is likely to create chaos - it gives us no opportunity
for scalability testing. It's not clear how versions in the existing world would
transition to the new one. Sync history could be lost everywhere.

We plan to bring the Metadata Server online while the legacy model continues to handle
traffic throughout the network. Stores can be converted one at a time, starting with
those that we control. This will provide functional reassurance as well as information
on the scaling and reliability of the new servers.

Once a representative set of Stores have been converted off the legacy representation,
we can convert Private Cloud appliances.


## Development

Feature work and bug fixes continue in the meantime.

So, as a development team, we need to keep the codebase releaseable, and yet avoid a scary
all-at-once integration point. Development of new servers is, of course, independent; but
the daemon and API integration require careful planning.

After considering the short list of realistic options, we chose the following:

A transitional design must simultaneously support both version models. Each Store is
either 'legacy' or 'centralized'. The Daemon must be updated to handle version updates
appropriately within each domain.

This lets us start to ship the code to end-users in Hybrid Cloud without requiring a
sudden upgrade. It is a key part of the gradual migration strategy described in
Tick Conversion.

Unfortunately, we lose the ability to quickly delete code until after the transition
to the centralized store is complete.


# Open questions, a partial list

 - project codename

 - API shim: clarify the ways we route and forward requests for API callers. Directly to
  the servers? Proxied?

 - If the Storage Agent has sufficient bandwidth within a private cloud deployment, is
there really any point in keeping a relay server? It is horribly bandwidth-inefficient
compared to the Storage Agent. And it is better for locality etc.
