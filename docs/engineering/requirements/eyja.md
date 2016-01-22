
# Eyja


# Purpose

This document describes behaviors for Eyja. This is an add-on to the [Eyja mockups](https://share.aerofs.com/l/67f506b1386a4f56947b619d111c1947#/). The document serves as a guide, but also a way to uncover hidden use cases.


#[work in progress]

Notifications Bubble Up

Mobile

Full-text Search

Sign Up

Admin Interface

Archiving

Scale 1,000 (users, groups, files, directories)

User Rights (Owner/Editor/Viewer)

Experience without a desktop. 
    - Users might try open a file on web or mobile, and reupload in the same conversation to 'sync them'. AeroFS currently does not support file syncing through uploading a file with the same name. We should think supporting it eventually.

AeroFS desktop interface. 
    - It needs a revamping to feel it is part of the Eyja product. For now we can live with using the existing interface and linking back to Eyja in some way. Example: 'Share This Folder' keeps the same old look. Once a user invites people, the notification might say to the user 'click to open the conversation in eyja'. 



# Definitions

- `Conversation` A set of messages between multiple people. Conversations can be:
	- `Group Conversation` A conversation between two or more people.
	- `Direct Conversation` A conversation between two people. 
	- `File Conversation` A conversation around a file.
	- `Folder Conversation` A conversation around a folder.
- `Conversation Member` Someone who is in a conversation.
- `Message` Self-explanatory. Bounded from the time the user starts typing until she hits 'Send'.
- `File Upload` Putting a file in a conversation, whether sent or synced.
- `File Update` A creation, deletion, or change of a particular file. 

A user sharing a file may:

- `Send a File`: Members can download and open the file. If a user edits the file, she will need to upload the new version as a separate new file.
- `Sync a File`: Members with a desktop client can open the file locally.

A notification may be:

- a `Tag Notification`. The '1' on the left panel. A notification that makes every reasonable attempt to inform the user immediately. A Tag Notification happens when:
    - someone sends a direct conversation
    - someone tags the user in a group conversation or file conversation. 
- an `Message Notification`. The bold on the left panel. An Update Notification happens when there is a new message in a conversation, or there is a change in the membership.
- a `File Update Notification`. A visual cue (file icon fill instead of outline). A File Update Notification happens when there is a file update. 

A group conversation may be:

- a `Public Conversation`. Messages, files and file updates can be seen by anyone.
- a `Private Conversation`. Messages, files, and file updates can be seen only by members. Direct conversations are private conversations.


# Desktop Interaction

1. A user with Eyja desktop has a `Eyja Root Folder` in their home directory. 
2. The Eyja Root Folder includes:
    - `Shared Folders`. Every shared folder has a corresponding group conversation in Eyja, using the folder name as conversation name. 
    - `Non-Shared Folders`. No non-shared folders appear in Eyja. 
    - A `"Direct Conversations" Folder`. The folder includes folders for every member in the organization, and its name is the full name of the member counterpart 'Mary Doe'.
3. A subfolder of a Shared Folder has a corresponding subconversation in Eyja, using the subfolder name as conversation name.
4. A file in a shared folder has a corresponding file conversation in Eyja, using the file name as conversation name. 
5. If a file is dragged to a Shared Folder or a subfolder, the file is synced. 



# Hierarchy

1. Conversations do not bubble up to the ascendant conversation. 
2. File Updates bubble up to the parent conversation. 


# Public & Private 

- Messages and file updates in public conversations can be seen by anyone. 
- Messages and files updates in private conversations can be seen only by members.
- Members can invite other people to join the conversation.
- People joining conversations cannot see the history of messages. [bonus] provide a way for the person inviting the member to decide if they should see the conversation.

