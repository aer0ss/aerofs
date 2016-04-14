# Current technical limitations of Eyja

## Overview
The dependencies the Eyja frontend (AeroIm) and backend (Sloth) are increasing to the point where our reliance on file-specific services has the ability to decrease performance. 

## Background

### Polaris File Transforms
File transforms are operations on files stored by Polaris.
The operations/transforms on files that we care about are the following:

1. `INSERT_CHILD` - A new file has been created
    * Contains the **FileId, FileName** of the newly created file
    * Contains the StoreId of the file's associated store
2. `REMOVE_CHILD` - An existing file has been removed
    * Contains the **FileId** of the removed file
3. `RENAME_CHILD` - An existing file has been renamed
    * Contains the **FileId, FileName** of the file after the rename
4. `UPDATE_CONTENT` - An existing file's content has been updated
    * Contains the **FileId** of the updated file

NOTE : A **Move** operation consists of a pair of {`INSERT_CHILD`,`REMOVE_CHILD`} transforms


### Eyja File Notifications
File notifcations are of one of the following forms:

1. `X renamed A to B`
2. `X created A`
3. `X removed A`
4. `X updated A`

These notifications can be aggregated into one of the following forms:

1. `X updated A,B,C`
2. `X created A,B,C`
3. `X deleted A,B,C`
4. `X updated A n times`

### Sources of Eyja File Information
At present, the Eyja backend (Sloth) surmises information about files in the following few ways:

1. File transforms received from Polaris
    * Sloth subscribes to file updates from Lipwig and will correspondingly query Polaris. 
    * Sloth can use information from past transforms and the current transform being parsed to surmise information about various files. 
2. When a new file-conversation is created, the name of the DB table (the filename) is stored in Sloth's database. This is the name of the corresponding file

## Problem
When generating file notifications, we need the name of the file being modified. However, the transform object retrieved by Sloth receiving these messages does not always contain the necessary information we need when going from a {Transform} -> {Eyja FileNotification Message}. As well, if we want the proper messages to persist properly, this must be handled on the backend and not the client. Note the following cases:

1. A `RENAME_CHILD` transform
    * The newname of the file is present but the old filename is not. 
2. An `UPDATE_CONTENT` transform
    * The name of the file being modified is not present
3. A `REMOVE_CHILD` transform
    * The name of the file being removed is not present 



## Proposed Solutions

1. Filename Cache in Sloth, and a route in Polaris to retrieve a file's Name
    * Filename Cache
        * A map between {FileId} -> {FileName}. This cache is populated from `INSERT_CHILD`, `RENAME_CHILD` events.
    * Polaris `getLatestFileName` Route
        * A new route is added to Polaris that returns a fileName given a StoreId, ObjectId and LogicalTimestamp. The name returned is the name of the file prior to the logicaltimestamp value. This way, if an `UPDATE_CONTENT` event is received and the corresponding file is not in the cache, we can use the route to retrieve the name.
    * Assuming Sloth never crashes, it will receive ALL file notifications/updates for objects part of a shared folder attached to a conversation. Using this linear stream of events, Sloth can keep track of the name of all files part of this shared folder
    * However since it is possible Sloth crashes and restarts, it may miss key events like `INSERT_CHILD, RENAME_CHILD`. In these cases, the Polaris Route is used to retrieve the name of the file prior to the logical timestamp of a given transform. 
    * **This is the current implementation but will not merged for the following concerns:** 
    * Cons
        * There is not a performant way to retrieve the name of a file of prior to a given logical timestamp in the polaris database. It will always involve selecting a large amount of rows prior to that logical_timestamp. 
        * The filecache's size can be limited to store only hot entries but this will increase strain on Polaris via that route. Memory Usage vs. Polaris-performance

2. Attach more relevant file information to the transforms received by Sloth. 
    * For the file-transforms that do not provide sufficient information to generate a message (Renames, Removes, Updates), modify Polaris to tack on the necessary information
    * Pros
        * Simplifies work in Sloth
    * Cons
        * Adds complicated work to Polaris
        * This would required a lengthy migration task for all previous customers so that the database states would be consistent. (To only keep the schema for database transforms starting with Eyja would be dirty)
3. Ad-Hoc temporary solution using just the fileCache and files that have conversations
	* For every conversation with an associated sharedfolder, underlying file conversations store the filename as the conversation name. For all file-notifications for files with a conversation (and thus in that table), we can correctly retrieve and modify the information (producing correct messages). 
	* For file-notifications on files that have no associated conversation, we can do *something*
	    * We still have the option of putting entries in the fileCache that have no corresponding file-conversation. In those cases, if the entry is in the cache (because of a past Rename/Create transform) we can properly populate a message ELSE we do not fill in the necessary fields. 
	    * Cons
	        * This will be undone once a final solution is formulated
	    * Pros
	        * Majority of cases just *work* but leaves the messages in the database in a bad state. 
4. Refactor Sloth and Polaris to be more-coupled?
    * A major refactor to Sloth and Polaris so that Sloth can have access to necessary file information without jumping through hoops. Exact details TBD. 

5. Use a special queue of transforms in Polaris for ingestion by Sloth (builds on option 2)
    * This is the design Hugues describes at https://gerrit.arrowfs.org/#/c/7302/
    * Sloth currently taps into the same transform stream as the desktop client, but we can create another more tailored to Sloth
    * The objects in this new queue would have all necessary fields to generate useful file messages for Eyja
    * To prevent the growth of this queue, every time Sloth retrieves all of said transforms, the queue is emptied. 
    * Having a separate queue prevents tacking on extra fields to transforms received by clients increasing latency and bandwidth requirements. 
    * A side benefit is `MOVE` operations can be encoded as `MOVE` instead of `INSERT -> DELETE` operations.

## Decision

The decision is to proceed with option 5 given the current deadline. However, option 2 is the desired option once Sloth <-> Polaris relationship is rearchitected. 