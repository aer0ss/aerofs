
# Eyja 


# Say Goodbye to 'where is the file?'


“Where is the file?"<br>
"I put it in our folder."<br>
"What folder?"<br>
"The one I shared with you last week."<br>
"I can’t find it."<br>
“Check your email, I have sent you an invite."<br>
"Can’t you just send the file to me?"<br>
"OK... hang on... there."<br>
“Thanks. Does it have the edits I sent last week?"<br>
"What edits?"

Eyja brings your work into one place. You will never have to ask 'where is the file?' again.

[Latest Eyja Mockups](https://share.aerofs.com/l/67f506b1386a4f56947b619d111c1947#/)

### Basics

`Conversation` A set of messages between multiple people. Several types exist:

- `Group Conversation` A conversation between three or more people.
- `Direct Conversation` A conversation between two people. 
- `File Conversation` A conversation around a file.

`Subconversation` A group conversation that is a child of a conversation. e.g: Marketing > Events. 

`Conversation Member` Someone who is in a conversation.

`Message` Self-explanatory.

`File Upload` Putting a file in a conversation, whether sent or synced.

`File Update` A creation, deletion, or change of a particular file. 

`Send a File` Members can download and open the file. If a user edits the file, she will need to upload the new version as a separate new file

`Sync a File` Members with a desktop client can open the file locally.

### Notifications

`Tag Notification` The badge '1' next to messages. A notification that makes every reasonable attempt to inform the user immediately. A Tag Notification happens when:
    - someone sends a direct conversation
    - someone tags the user in a group conversation or file conversation. 

`Message Notification` A message in bold. An Update Notification happens when there is a new message in a conversation, or there is a change in the membership.

`File Update Notification` Currently behaves like a message notification. 

### Public & Private

`Public Conversation` Messages, files and file updates can be seen by anyone.

`Private Conversation` Messages, files, and file updates can be seen only by members. Direct conversations are private conversations.

### Desktop 

A user with Eyja desktop has a `Eyja Root Folder` in their home directory. 

A `folder` of a Shared Folder has a corresponding conversation in Eyja, using the subfolder name as conversation name.

A `file` in a shared folder has a corresponding file conversation in Eyja, using the file name as conversation name. 

The `conversation tree` is the same as the `shared folder tree`. 

The Eyja Root Folder includes:

- `Shared Folders` Every shared folder has a corresponding group conversation in Eyja, using the folder name as conversation name. All its children and below have a corresponding conversation as well. 
- `Non-Shared Folders` No non-shared folders appear in Eyja. 
- `Direct Conversations" Folder` The folder includes folders for every member in the organization, and its name is the full name of the member counterpart 'Mary Doe'. All its children and below have a corresponding conversation as well.

If a file is dragged to a Shared Folder or a subfolder, the file is synced. 

### Conversation Bubbling

Conversations do not bubble up to the ascendant conversations. 

File Updates bubble up to the ascendant conversations. 

### Public & Private

`Public Group` A group conversation where name, messages, members, and file updates can be seen by anyone. 

`Private Group` A group conversation where name, messages, memebers, and file updates can be seen only by members.

`Group Members` can decide to make a private group public, and vice-versa. 

People joining private conversations, as well as people that see a public group, cannot see the messages that were sent before their arrival. 


### Work in Progress

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

Reminders on messages
Ability to add `add a reminder` to a message and get reminded

Unread



