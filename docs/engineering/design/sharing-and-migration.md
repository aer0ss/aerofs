A short primer on ids, sharing and migration
===


All objects are equal but some objects are more equal than others
---

We use universally unique identifiers (UUID) to identify objects within AeroFS.

These objects fall in two spaces, further subdivided into subspaces:

* Object IDs aka OID
   - regular OIDs (file and folders) are randomly generated (UUID v4)   
   - anchor OIDs are derived from the OID of the folder that was shared by
   resetting the version nibble of the UUID (which would make them UUID v0 if
   such a thing existed but we're simply taking advantage of some room left by
   the standard to easily subdivide the OID space)
* Store IDs aka SID
   - regular SIDs (store created by sharing a regular folder) need an efficient
   1:1 mapping to their associated anchor, hence the decision to use the identity
   function. They are therefore "v0" UUIDs
   - root SIDs are created by computing a salted MD5 hash of the user id. To
   avoid collision with regular SIDs we give them a version nibble of 3 (that
   choice was made because the definition of UUID v3 is closely related)

The relationships between the different ID subspaces and the ability to uniquely
and efficiently map corresponding subspaces are crucial to the sharing flow.

Objects are usually referred to using an SOID which is basically a (SID,OID) pair,
with a twist: to reduce storage requirements we an SIndex (32 int) instead of an
SID (128bit UUID).

Further references can be made to specific "components" or "branches", or both
of a given object, resulting respectively in SOCID, SOKID and SOCKID but these
are not relevant in this document.


Sharing is caring
---

When a user clicks the "share folder" item in the context menu an awful lot has
to happen under the hood before the contents can be synced with other users.

First the client derives the SID for the new store and contacts SP to inform it
of the creation of a new store and of the list of users who are fortunate enough
to have been granted access to it.

Membership to a store is determined by a centralized ACL table stored on SP.
Clients regularly synchronize their local ACL table to the central one to ensure
they can enforce access control when communicating with other peers. This sync
is done via a push/pull mechanism: the client pulls on startup and whenever it
receives a push notification informing it of ACL changes affecting it.

But the meat of the operation only starts once SP acknowledges the successful
creation of the shared folder. At that point the daemon needs to convert the
regular folder into a shared one, which is achieved by "migrating" all the
children of said folder to the newly created store. (see below about migration).

NB: If the daemon crashes after a successful SP call and before a successful
conversion the user will need to retry sharing the folder until the conversion
succeeds.

ACL management
---

Each ACL entry is associated with a flag indicating the status of the entry. It
can be any of PENDING, JOINED, LEFT. Only JOINED entries are propagated to
the clients.

  * a new ACL entry is added when a user is invited. It is marked as PENDING and
    therefore is not propagated to other members of the shared folder.
  * when the user decides to join the shared folder, the flag changes to JOINED
    and the ACL entry is propagated to other members of the shared folder.
  * when a user decides to leave the shared folder, the ACL entry goes to LEFT,
    and is physically deleted from all the user's devices. The user can view
    deleted folders on the Web UI, and can "rejoin" or "forget" about the folder.
    Rejoining moves the folder's status back to JOINED; forgetting remove the
    ACL entry completely from the database.
  * when a user is kicked out from the shared folder, the corresponing ACL
    entry is completely removed from the database.
  * If a user is an owner of the folder, he can "destroy" the folder, causing
    all the members (including himself) kicked out from the folder and the
    folder's data completely removed from the SP database. Alternatively, the
    system can convert the shared folder to normal folders. We call this
    "unshare". See the next section for information about unsharing.
  * If the user is an owner and only member of the folder, his forgetting the
    folder causes the folder to be destroyed automatically.
  * If the user is the only owner of a folder and the folder has other members,
    the user is not allowed to forget the folder unless he add other members as
    owner or remove all other members.
    Alternatively, we can automatically destroy the folder. This is what box.com
    does. Their confirmation message is: "Are you sure you want to delete the
    folder "***" and all its subfolders? Any collaborators in the folder will no
    longer have access."

Also see team_management.txt for interactions between shared folders and teams.


The difficulty of unsharing
---

As mentioned above, rather than destroying a shared folder on all the devices,
the system can convert the shared folder to normal folders.

However, we cannot reuse the original folder OID as it would lead to unexpected
behaviour down the line. Consider the following scenario:

* Device D, which belongs to user U, shares a folder with Device E, which belongs
  to user V.
* As an admin, user U unshares the folder for both U and V.
* Device D receives the notification and completes the convertion.
* User U on D reshare the converted folder to other users.
* Now Device E comes online. Because of the 1:1 mapping between store and folder
  IDs, the newly shared folder on D will have the same store ID as the old store
  on E. And because V is no longer in the new shared folder, E will receive an
  ACL update that causes the old folder to be destroyed rather than unshared.

To allow users to re-share the unshared folder independently, the new OID should
be different for all members of the shared folder being unshared. To prevent
breakage, the OID must be identical on all devices belonging to a given user.
This essentially leaves two options:

* generating a new OID on the server and propagating that to the client.
* generating the new OID on the client using a deterministic algorithm that uses
  the user ID as one of its input.

In any case, unsharing will require other non-trivial changes to the
infrastructure so we don't need to settle this implementation detail right now.


The external flag
---

Beside sharing folders under the root anchor, AeroFS allows sharing arbitrary
folders outside the the root anchor. This gives a lot more flexibility to users
but comes with a different class of constraints. In particular, if a folder is
outside of the root anchor on one device it must be outside the root anchor on
all devices owned by the same user (this is required to provide a consistent
UX and avoid introducing complex special-casing in the core sync algorithm).

Enforcing this constraint is done via an extra flag in the (SP) ACL table. The
"external" flag is set when a user creates a share folder or joins an existing
one. This flag cannot be changed, short of leaving the shared folder and rejoinig
it.

The flag is propagated in the GetACLReply. Regular clients automatically join
shared folders whose external flag is not set. Folders marked as external cannot
be automatically joined and are placed into a special table of "pending roots".

The GUI keep tracks of the apparition of such "pending roots" via Ritual and
allow the user to "link" them to an existing folder on the local filesystem.
Linking must be done on each device.

NB: the external flag is a per-user and per-shared folder property. A user
can have both external and non-external shared folders and a shared folder
can be external for some users and non-external for others.

The Team Server does not pay attention to his own external flag. However, it
needs to automatically create anchors in users' root stores to be able to
correctly service Content API calls. to that end it receives the external
bit for all members of the store.


Migration
---

Migration is basically a "move across store boundary". Due to the way we store
objects in the OA table and the way the core protocol propagates update, such a
move is actually decomposed in the re-creation of the whole object tree in the
destination followed by the deletion of the object tree in the source.

The primary purpose of the migration subsystem is to avoid redundant transfers
caused by such cross-store movement. This is achieved by keeping the same OID
and preserving version vectors when migrating an object. Because such transfers
are not an issue for folders, they do not receive any special treatment (i.e.
migration is a "dumb" create+delete sequence for them which does not preserve
version vectors).

Because the network offers no guarantee as to which of the two messages (create
or delete) will reach peers first, we need to ensure that a cross-store move can
be detected regardless of the order in which they are received.

This is achieved by:

  * preserving OID, which allows to search for another object sharing the same
    OID in a different store when a create message is received, and migrate any
    such object
  * renaming the deleted object to indicate in which store it has been moved

One of the core invariants of the migration subsystem is that a given OID can
only be admitted in a single store at any given time (on a given device). This
invariant is obviously incompatible with multitenancy (aka Team Server) so the
whole migration subsystem is disabled in that context. In addition, because
folders are not migrated the same way files are, this invariant does not always
hold for them, although it does in a steady state (i.e. when all peers are in
sync)

To further complicate matters, a double standard is applied to anchors. We do not
support nested shared folder and prevent users from creating them directly.
However, until recently it was possible to create nested shared folders by moving
anchors around on the file system. We now convert stores back to regular folders
when the user moves an anchor to a different store but we still accept store
migration as a result of network messages.


It mostly works, except when it does not
---

Migration is a tricky business. Because we want to track versions despite cross-
store movement we need to preserve OIDs which can result in surprising behaviors
in some corner cases where migration "backfires"

For instance, consider two devices sharing the following hierarchy

    anchor:foo
      dir:bar
        file:baz
    anchor:qux

The following sequence of events

  1. bar is moved under qux on device A
  2. foo is expelled on device A before the deletion propagates to device B
  3. foo is readmitted on device A

Will result in the following hierarchy

    anchor:foo
      dir:bar
        file:baz
    anchor:qux
      dir:bar

Similarly, because we want to avoid nested shared folders, we convert anchors to
regular folders instead of migrating them as-is. This can lead to other examples
of unexpected and counter-intuitive implicit file movements.

For instance, consider two devices, owned by different users, sharing the
following hierarchy

    anchor:foo
      dir:bar
        file:baz
    anchor:qux

The following sequence of events

  1. foo is moved under qux by user A on the filesystem
  2. discovering that foo was converted to a regular folder, user A re-joins foo

Will result in the following hierarchy

    anchor:foo
      dir:bar
        file:baz
    anchor:qux
      dir:foo
        dir:bar

Because such moves are not expected to be very frequent, the corner cases even
less so also because revision history ensures that no data is lost, these issues
are not currently assigned a high priority. However, any modification to the
migration subsystem that would contribute to solving them would be most welcome.


Further research
---

We suspect that it would be possible to achieve the goal of migration, i.e avoid
redundant transfers and prevent loss of updates when moving objects across stores
but we have yet to come up with a satisfying design.

Some aspects worth investigating:

  * keep version vectors local to a store
  * use content hashing
  * generate new OID during migration (would need to be encoded into the name of
    the deleted object)
