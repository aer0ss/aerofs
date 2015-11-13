# Sync Status

## Prelude

Users often ask the following questions when using the AeroFS product.

- I have opened my laptop and I want to make sure I am working on the latest version. When can I
  start editing this file without creating a conflict?
- I have edited a document I want my coworkers to see. When will I be able to switch off my device?
- AeroFS tells me I am not up to date. What needs to happen? How long will it take to be up to
  date?

The sync status feature endeavors to answer these questions.

---

## Overview

Sync status answers the simple question

    Is my AeroFS client up to date and backed up?

### Commentary

In the context of Dropbox, "Sync Status" means your content has synced to the central server. The
corollary here is, of course, your content is backed up.

In the context of AeroFS, your content may have indeed been synced, but it may or may not be backed
up (see definition below).

FUTURE: We might consider a naming enhancement here, e.g. "Backup Status", "Sync and Backup
Status", etc. For now, we will stick with the public facing name of Sync Status because it is what
end-users expect from Dropbox, and therefore from us.

In this document we may refer to "in sync". This is synonymous with "up to date and backed up".

## "Up to date"

Up to date means:

### User facing

1. My client has the latest changes from my peers, and
2. My peers have the latest changes from my client.

### Technical spec

We have metadata consensus with polaris.

## "Backed up"

Backed up means:

#### Commentary

This definition of backed up is not perfect. It is reasonably easy to poke holes in it. For
example,

1. What if there is no TS/SA? In this case the Sync Status feature provides no value.
2. What if the TS that has the content goes down as well, and there is no other replica? In this
   case, indeed, the content may be lost.

Both examples here boil down to one truth: the burden is on the (customer's) system administration
team to deploy and manage a proper TS/SA (cluster). Otherwise, the sync status feature is
non-functional or can be rendered non-functional.

### User facing

If my device catches fire, I will be able to retrieve all AeroFS files from some repository. All
hope will not be lost in this scenario.

### Technical spec

We have metadata concensus with polaris, AND, at least one Team Server or Storage Agent has this
content version.

---

## Client

The AeroFS client is in sync if all AeroFS roots are in sync.

### Folder

A folder is in sync if:

1. Its has metadata consensus with polaris (i.e. it is itself up to date), and
2. All folders and files under it are in sync.

### File

A file is in sync if it is up to date and backed up.

---

## Visual Feedback

### In sync

Icons on:

- Folders
- Files

### Not in sync

Icons on:

- Tray menu
- Folders
- Files

### Syncing

Icons on:

- Tray menu
- Folders
- files

Tray menu should also display:

- The files are syncing
- Size
- FUTURE: Progress bar (indication when it will end)

### Error

In other words, conflicts, or unsyncable files.

Icons:

- Tray menu
- Folders
- Files

Tray menu should also display:

- What files have a problem
- A way to go to the containing folder or the files themselves.

---

## Future, Not in Scope

1. "Sync Status" naming enhancements.
2. Global progress bar (i.e. answering the question, how long will it take for me to get in sync).
3. One possible enhancement to the visual feedback section is excluded. Specifically, the case
   where a client is in sync but is still syncing content to another peer. In this case, our
   visual feedback display will indicate that we are syncing, i.e. not in sync. There is
   potentially another state here: "in sync and syncing". Such a state will be confusing to the end
   user and is an edge case, so it is ignored for now.
