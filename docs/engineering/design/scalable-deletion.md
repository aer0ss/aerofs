Scalable deletion, expulsion and migration
==========================================

## Problem

The naive approach of walking the affected subtree within a single
db transaction when deleting, expelling or migrating of folder does
not scale when large folders are affected.

It significantly increases the likelihood of one step of the operation
failing, causing an expensive and even more fragile rollback to be
attempted.

Besides it risks causing an OutOfMemory error, wherein the operation
will take a while, fail miserably and may quite possibly leave the
db and filesystem in a inconsistent state.

Worse yet, when a file is locally deleted or migrated this issue will
cause a crash-loop with the client detecting the change, attempting to
update the db, crashing and restarting only to fail in the exact same
way. This crash loop not only causes a persistent no-sync but it also
wastes tremendous amounts of CPU and network bandwidth in the process
due to repeated scans, db operations and defect sending (which will
most likely include hprof files).

## Solution

The only way to address this issue is to re-architect the relevant
code paths such that either the recursive walk is avoided or it can
be split in multiple db transactions, thereby preventing the crash
loop and allowing incremental progress to be made even in the face
of other unrelated crashes.


### Expulsion

When expelling a folder it is unfortunately impossible to avoid a
full walk as the db entry of every affectedfile needs to be touched
to reset FIDs and delete CAs.

It would however be possible to split this walk in multiple smaller
transactions.

This will require a number of changes to the core:

 - The expulsion status can no longer be stored in the OA. Instead
   of a simple flag check code that checks for expulsio will have
   to go up the hierarchy to check whether any parent is expelled.
   This is technically slower but:

     * Going up the chain is o(log n) which is unlikely to be a
       problem
     * Most code checking for expulsion already walks up the hierarchy
       to get a Path so the slowdown will be hardly noticeable

 - A new concept of a "staging area" will have to be introduced.
   Instead of a large transaction with a subtree walk, expulsion
   becomes a small transaction that puts the affected folder in
   this staging area. From there the walk can be done in small
   increments ensuring that progress is made even in the face of
   unrelated crashes.

The staging area has two aspects:

 - logical: a DB table to keep track of folder that need to be
   walked to complete the expulsion process

 - physical: an auxroot folder containing the physical subtree
   affected by the expulsion

An important side-effect is that operations that may have previously
been aborted due to race-condition-induced mismatch between db and
filesystem will now succeed immediately. This also means that the
code that cleans up the staging area must be designed to handle
mismatch between the logical and physical objects.


### Admission

The flip-side of expulsion is re-admission, which can use a similar
staging-area to re-populate the affected subtree. The biggest caveat
there is that once a folder is re-admitted, its children will start
syncing.

This can be addressed either by incrementally re-populating the whole
subtree in the staging area and moving it in one small concluding
transaction or by incrementally re-populating inside the root anchor
and marking non-repopulated children as expelled to avoid creating
confusion in other parts of the code.

The first approach is conceptually cleaner but would be very confusing
to the user the the second approach is probably a better choice.


### Deletion

Deletion in AeroFS is a move under a special trash folder. This
by itself is a small atomic operation, however the trash folder
is expelled so deletion implies expulsion of the affected subtree.

The same staging area used for explicit expulsion will work just
as well for implicit expulsion.


### Migration

Migration, aka cross-store move is essentially equivalent to a
delete+create sequence with a twist to preserve happens-before
relationship.

To allow this transaction to be split, the easiest and most
robust approach is probably to rely on the above work for the
deletion side and to lean on the existing scanner infrastructure
for the creation side. This will require some careful adjustments
to correctly propagate immigrant versions and robustly handle
race conditions.

TODO: consider **not** flattening the subtree as part of this
migration re-architecting. This could significantly reduce network
traffic induced by migration as well as the likelihood of race
conditions between emigration and immigration.

