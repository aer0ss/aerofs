# Metadata Server

This document outlines the high-level functions, API and implementation of the AeroFS centralized metadata server.

## Definitions

**SHARED FOLDER**: **FOLDER** shared between multiple users.
Corresponds to an actual folder on the device's physical filesystem.
The folder on the physical filesystem may have a different name.

**FILE**: Representation of a file on a device's physical filesystem.

**FOLDER**: Representation of a folder on a device's physical filesystem.

**DEVICE**: An AeroFS client. This may be a mobile client, an API client, a desktop client or storage agent.
A **DEVICE** is represented by a **DID**.

**OBJECT**: An object tracked by the AeroFS virtual filesystem.
Every **OBJECT** is represented by an **OID** and a **TYPE** and has an associated **VERSION**.
An **OBJECT** is created with an initial **VERSION** of 0.

**VERSION**: A scalar >=0 that is incremented whenever an **OBJECT** is added, deleted or modified.

**STORE_VERSION**: The version is set to 0 when the shared folder is created and incremented by 1 whenever:

* any object is added to the shared folder
* any object is deleted from the shared folder
* any object is modified


**CONFLICT**: Created when multiple **DEVICE** instances make independent changes to an **OBJECT** with the same **VERSION**.

**MOUNT**: Mount point for a shared folder. A mount is scoped to a user.

**TYPE**: Type of a tracked object. A **TYPE** is always associated with an **OID**. Supported types are:

* **FILE**
* **FOLDER**
* **MOUNT**

**DID**: Device ID. Unique identifier of every AeroFS client. They are generated independently by each AeroFS client.

**OID**: Object ID. Unique identifier of every tracked object in AeroFS. These are generated independently by each AeroFS client.

**SID**: Shared Folder ID. Unique identifier of every shared folder in AeroFS. These are generated independently by each AeroFS client.

This document refers to a tracked object using the terms **OID** or **OBJECT** interchangeably.

## Supported Operations

This section outlines the *functional requirements* for the metadata server.
In addition to these functional requirements the metadata server **MUST** be scalable, easy to introspect and administer.

### Happy Path

The following operations should be supported:

* Object creation
  * Create a new file
  * Create a new folder
  * Create a new folder hierarchy
  * Undelete a previously deleted file (**TBD**)
* Object modification
  * Change a file's name
  * Change a folder's name
  * Change a file's timestamp, permissions, etc.
  * Change a file's content
* Object moves
  * Move a file to another folder in the same shared folder
  * Move a file to another folder in a different shared folder
  * Move a folder to another folder in the same shared folder
  * Move a folder to another folder in a different shared folder
* Object deletion
  * Delete a file
  * Delete an empty folder
  * Delete a non-empty folder (may be a folder hierarchy with multiple files and sub-folders)
* Object queries
  * Get the metadata for the latest version of a file
  * Get a list of the metadata for all previous versions of the file
  * Get a list of all deleted files in a shared folder
  * Get the children for the latest version of a folder
  * Get the list of devices that have the content for any version of the file
  * Get the list of changes to an arbitrary folder in a directory tree since a given version

In addition to the above, the metadata server **MUST** properly handle conflicting changes to the same object.

### Other Cases

In addition to the supported operations above the metadata server **MUST** also handle the following:

* Adding a file to a non-existent folder
* Adding an object, but an object with that name already exists
* Adding an object, but an object with the same name was previously deleted
* There are multiple deleted files of the same name
* Share a folder but it has already been shared
* Share a folder but it has been moved or deleted
* Method of content hashing has changed
* No devices have the content for an OID
* Permissions
  * Adding an object to a shared folder for which you do not *currently* have permissions. The server **MUST** reject this operation.
  * Modifying an object for which you do not *currently* have permissions. The server **MUST** reject this operation.
  * Deleting an object for which you do not *currently* have permissions. The server **MUST** reject this operation.
* Conflicts
  * Modifying an object, but a newer version of that object already exists
  * Modifying an object, but the object has already been deleted
  * Moving an object, but it has already been moved or deleted
  * Deleting an object, but a newer version of that object exists
  * Deleting an object, but the object has already been deleted

Unless noted otherwise all operations on a shared folder require that a device *currently* has the appropriate permissions for that folder.
Historical permissions **MUST NOT** be considered.

## Architecture

The metadata server is the *only* access to the centralized metadata database.
All client communication with the metadata server will be via a REST API over HTTP. We make the following assumptions:

* The initial point of contact for a client will be nginx.
* Clients make an HTTP 1.1+ SSL connection to nginx, which SSL terminates. The request will be sent via HTTP to the backend metadata server(s).
* There may be multiple independent instances of the metadata server.
* There may be multiple independent instances of nginx.
* There is a *singular* logical instance of the database (it may be a cluster of physical or virtual machines).
* There is a *singular* logical notification server that is *separate* from the metadata server.
* nginx adds the certificate serial as an AeroFS header to the proxies HTTP request.
* ACLs will be stored and handled in SP as currently.

The MVP will not contain a separate caching layer: all reads and writes go directly to the metadata database.
ACLs will be checked *on every single request* via a **yet-to-be-built** ACL library.

### Implementation Recommendations

* PostgreSQL for the database. Postgres is reliable, has strong ACID guarantees, UTF-8 support and excellent clustering behavior.
* JDBI for database access.
* netty/jersey aka. restless as the metadata backbone. Investigate moving to netty 4.x and jersey 2.x for better performance and async servlets.

### Known issues

Since there may be multiple metadata servers it's possible for two sequential updates to a store to be submitted to the notification server out-of-order.
We will implement the client to be resilient to out-of-order updates. With this option the notification server simply functions as a wake-up system.

## Schema Design

This section describes the tables in which metadata is stored. Each sub-section is titled after the table name and outlines:

* Purpose of the table
* Column names and descriptions
* Indices and use cases

We prioritize the following operations:

* Adding a new object
* Updating an existing object
* What changes have occurred in a store since a certain time

Unless otherwise specified, all strings *MUST* be stored as UTF-8.

Although we assume that it is the system admin's responsibility to provision the database,
the schema should allow for data to be easily partitioned over multiple physical instances in a single cluster.

### objects

All the current objects in the AeroFS virtual filesystem. This table does not include deleted objects.

    |sid|oid|version|parent|name|type|

**sid**: shared folder to which the object belongs

**oid**: object the change applies to

**version**: version associated with this change

**parent**: OID of the parent, or NULL if this is a top-level object in a shared folder

**name**: object name (this is not the full path name)

**type**: object **TYPE** (once set it is never changed)

#### Indices

*(sid)*: List all objects in a shared folder

*(oid)*: Find a specific object

*(sid, parent)*: Find all objects in a store with the given parent

### transforms

Append-only log of changes made to every object. Also contains an entry for the *latest* change for the object.

    |id|sid|oid|version|change_type|child|name|

**id**: global counter that increments on every object change

**sid**: shared folder to which the object belongs

**oid**: object the change applies to

**version**: version associated with this change

**change_type**: **CHANGE_TYPE** describing this change

**child**: OID if **change_type** is **ADD_CHILD** or **DEL_CHILD**; null otherwise

**name**: object name (this is not the full path name)

Valid **CHANGE_TYPE** instances include:

* **CONTENT**
* **ADD_CHILD**
* **DEL_CHILD**
* **RENAME** (change the name of the object without changing its location in the virtual filesystem)
* **TOMBSTONE** (when an object is deleted or removed from a shared folder)

#### Indices

*(id)*: Find all changes since a given point in time

*(sid, id)*: Finall all changes for a store since a given point in time

*(oid, version)*: List all versions for an object. Find a specific version of the object

*(sid, oid, version)*: List all changes made to an object when it was in the given store

### devices

List of devices that have the content for a specific object version.

    |oid|version|device|

**oid**: object whose content is available on the device (**all objects in this table must have type FILE**)

**version**: version of the object whose content is available on the device

**device**: DID of the device with the content

#### Indices

*(oid, version)*: List the devices that have the specific version of an object

### content

List of content properties for files in AeroFS. This may be sparse
(only populated when content changes)
or complete (populated on *every* object modification)

    |oid|version|content_hash|content_mtime|content_size|

**oid**: object whose content is available on the device

**version**: version of the object whose content is available on the device

**content_hash**: hash of the content (hashing method is left as an implementation detail)

**content_mtime**: epoch when the content was modified

**content_size**: positive content length

#### Indices

*(oid, version)*: List the properties for the specific *(oid, version)* pair

## Conversion

### Converted

Historical table that maps an OID to a corresponding distributed tick.

    |oid|distributed_version_tick|

**OID**: object ID (UUID; generated by each client)

**distributed_version_tick**: string representation of the *latest known* distributed tick for the object.
Takes O(n) space, where n is the number of devices that have contributed to the distributed version.

#### Indices

*(oid)*: Get the list of all converted oids. Get the latest distributed tick for an object.

## API

This section outlines the REST API calls that are provided by the metadata service.
All calls **MUST** check ACLs for the requester.
If the requester does not have permission to make the API call it must return a **403**.

### POST /objects/{oid}

Make an update to an object. Request has the following body:

    {
        "base_version": 7,
        "change_type": "CHANGE_TYPE"
        ...,
    }

#### Parameters

* **clobber_version**: true if the user wants to force the object to be overwritten even if a conflict exists

#### Request Body

The request body contents depend on the change type:

**CHANGE_TYPE** of **ADD_CHILD**:

* **base_version**: version of the parent object to which the change is being applied
* **oid**: parent object
* **child**: OID of the child being added

**CHANGE_TYPE** of **DEL_CHILD**:

* **base_version**: version of the parent object to which the change is being applied
* **oid**: parent object
* **child**: OID of the child being removed

**CHANGE_TYPE** of **RENAME**:

* **base_version**: version of the object to which the change is being applied
* **oid**: object being renamed
* **name**: the new name

**CHANGE_TYPE** of **CONENT**:

* **base_version**: version of the object to which the change is being applied
* **oid**: object whose content is being updated
* **content_hash**: non-empty content hash
* **content_size**: long >= 0 size of the content
* **content_mtime**: epoch associated with the content

#### Return Codes

**200**: The update was accepted. Response has the following body:

    {
        "assigned_oid": "actual_oid"
        "assigned_version": 8
    }

**409**: The update could not be accepted because there was a conflict with the latest-known version on the server.

#### Response Body

**assigned_oid**: object OID assigned by the server for a new object (this may be the same as the one sent)

**assigned_version**: version >= 0 assigned for this change

### DELETE /objects/{oid}

Delete an object.

    {
        "base_version": 10
    }

#### Parameters

* **force_delete**: set to true to force a deletion regardless of conflict

#### Request Body

**base_version**: version of the object to be deleted

#### Return Codes

**204**: object was successfully deleted

**404**: object to be deleted was not found

**409**: object could not be deleted because there was a conflicting update

#### Response Body

None.

### PUT /objects/{oid}/{version}/devices/{device}

Indicate that this device has downloaded the content for a specific version of an OID.

#### Parameters

None.

#### Request Body

None.

*FIXME (AG): does adding a device to this list imply that it cannot serve an earlier version of the content?*

#### Return Codes

**204**: update was accepted

**404**: the {oid}-{version} pair does not exist

#### Response Body

None.

### GET /objects/{oid}/{version}/devices

Return a list of devices that *may* serve the content for the {oid}-{version} pair.

#### Parameters

None.

#### Request Body

None.

#### Return Codes

**200**: valid object

    {
        "devices": ["did0", "did1", ...],
        "known_obsolete": "true/false"
    }

**404**: {oid}-{version} pair does not exist

#### Response Body

*FIXME (AG): this is a response body*

### DELETE /objects/{oid}/{version}/devices/{device}

Delete a device from the list of devices that *may* serve the content for an {oid}-{version} pair.

#### Parameters

None.

#### Request Body

None.

#### Return Codes

**204**: device was deleted from the list. noop if the device was not serving the content anyways

**404**: {oid}-{version} pair does not exist

#### Response Body

None.

## Test Environment

I recommend two different test environments:

1. Single appliance. Consists of a single instance of the database, metadata server and notification server in a single virtual machine.
This is the appliance we give our customers.
2. Multi-server. In this setup we have multiple metadata servers behind multiple nginx instances (that round-robin load-balance).
The metadata server instances interact with a **database cluster** that consists of multiple machines.
The data is partitioned over these multiple machines.

keep the sid in changes
changes - devices doing reads
versions - devices doing writes, api queries (repeat sid)
root objects - anchors
store hierarchy table for acts (when you move shared folder into another shared folder)
sid <- generated deterministically;
