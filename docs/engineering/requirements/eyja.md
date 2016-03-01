
Eyja 
====

# Say Goodbye to 'Where is the file?'

"Where is the file?"<br>
"I put it in our folder."<br>
"What folder?"<br>
"The one I shared with you last week."<br>
"I can’t find it."<br>
"Check your email, I have sent you an invite."<br>
"Can’t you just send the file to me?"<br>
"OK... hang on... there."<br>
"Thanks. Does it have the edits I sent last week?"<br>
"What edits?"  
Eyja brings your work together in one place.

# Mockups

[Latest Eyja Mockups](https://share.aerofs.com/l/67f506b1386a4f56947b619d111c1947#/)


# Definitions


`Conversation` A set of messages between multiple people. There are several types of conversations:

* `Channel` A conversation between multiple people. Channels:
	* Channels must have a name, given by the user.
	* Channels can be either public or private.
	* Members can be added, or removed.
	e.g. 'Marketing'
	
* `Direct Conversation` A conversation between multiple people with the following properties:
	* Direct Conversations have an immutable name, with the first names of each member, ordered alphabetically.
	* Direct Conversations are private.
	* Members cannot be added or removed.
	e.g. 'Ned, Sansa, Tyrion'
	
* `1:1 Conversation` A Direct Conversation with only two people. Name for conversation is the first name of the other person talking.  
e.g. 'Jaime Lannister'
* `File Conversation` A conversation around a file.

`Conversation Subfolder` A folder that is below a channel or a direct conversation, and that may contain files and other conversation subfolders. It does not have any conversation associated with it. 

`Conversation Member` A user that has joined a conversation. 

`Conversation Message` Words from a member.

`File Update Message` Events about files in a conversation. e.g.: 'file updated', 'file deleted', 'file created'. 

`Eyja Desktop` The desktop version of Eyja, which includes file sync and share. 

`Eyja Web` The web version of Eyja.

`Eyja mobile` The mobile applications, either iOS or Android.

`File Upload` The action of sharing a file in a conversation.

`Send a File` A File Upload where recipients may download a copy of the file. If members want to share subsequent edits they made on the file, they would need to upload it again. Send a File triggers a File Update Message. e.g. 'Sansa sent raven.jpg'

`Sync a File` A File Upload where recipients with Eyja Desktop receive a copy of the file on their desktop. If members make edits, a new copy of the file is updated  to the other members.

`Tag Notification` A notification that makes every reasonable attempt to inform the user immediately. e.g. The badge '1' next to messages. 

`Unread Notification` A notification that tells the user she needs to look at it, and will change state once it is done. e.g. A conversation in bold. 

`File Update Notification` A small, unobstrusive signal that can be ignored. e.g. a small icon next to a recently updated file.

`Eyja Root Folder` The Eyja folder in the user home directory when user has installed Eyja Desktop.

`Shared Folder` A folder in the Eyja folder that is shared with members of the corresponding conversation. 

`Personal Folder` A folder in the Eyja folder that is not shared with anyone else.

`Inbox` A place that shows the latest Tag and Unread Notifications.

`Conversation Tree` The tree of folders and files below a conversation.

`Roster` The list of conversations.


# Behaviors

### Roster

The roster lists: 

* Channels
	* All channels the user belongs to.
	* Ordered alphabetically.  
	* All subfolders and files associated with the channel.

* Direct Conversations
	* Most recent 20 Direct Conversations. 
	* Ordered alphabetically.
	* All subfolders and files associated with the channel. 
	

### Privacy of Channels and Direct Conversations

A `Public Channel` can be found and joined by anyone. Name, messages, members, and file updates can be seen and searched by anyone.  

A `Private Channel` can be seen only by members. It cannot be joined. New members can be invited by existing members. Members can be expelled by existing members. Name, messages, memebers, and file updates can be seen by only by members.  

A Direct Conversation can be seen only by members. It cannot be joined. New members cannot be invited by existing members. Name, messages, memebers, and file updates can be seen only by members.  

A member of a public channel can make the channel private. 

A private channel cannot be made public. 


	
### Join, Invite, Leave, Expel

In Public Channels:
 
* A non-member can join.
* A member can leave.
* A member can invite another member.  
* A member can expel another member.  

In Private Channels:

* A member can leave.
* A member can invite another member.
* A member can expel another member.
* A non-member cannot join.

In Direct Conversations:

* No member can leave, join, invite, or expel. 

	
### Direct Conversations are Unique

Direct Conversations are unique. If a user creates a conversation B with members that already share a conversation A, user comes back to the conversation A. 



### File Conversations

File Conversations cannot include files or folders.  
File Conversations exist only within either a Channel or a Direct Conversation.                



### Desktop: Folder Locations

Eyja Location: ~/Eyja  
Eyja Channels -: ~/Eyja/
Eyja Direction Conversations Location: ~/Eyja/Direct Conversations/

e.g. 1  
Group Conversation 'White Walkers'  
Folder: ~/Eyja/White Walkers   

e.g. 2  
Direct Conversation with 'Arya Stark'
~/Eyja/Direct Conversations/Arya Stark/  



### Notifications 


##### Tag Notifications

A Tag Notification happens either when:
 
* someone sends a message to you  
* someone tags you. e.g. '@Stannis'

A Tag Notification:
 
* shows the count '1': 
	* left panel: on the conversation
	* desktop: on the app icon
	* web: on the favicon
	* iOS: a badge on the app icon
* bolds the conversation
* adds the conversation to the Inbox

Under some conditions, it may:

* trigger a sound notification
* trigger a mobile notification
* trigger a desktop notification 
* trigger an email notification

See 'Connected, Active' for triggering conditions.

    
##### Unread Notifications
An Unread Notification happens either when:

* someone sends a message in a group conversation you belong to
* someone updates a file you uploaded  
* someone updates a file you edited  
* someone updates a file where you left a comment  
* someone comments on a file where you left a comment  
* someone comments on a file you uploaded 
* someone comments on a file you edited  
* someone deletes a file you uploaded  
* someone deletes a file you edited  
* someone deletes a file you commented on
* someone renames a file you uploaded
* someone renames a file you edited
* someone renames a file you commented on

An Unread Notification:

* bolds the conversation
* adds the conversation to the Inbox


##### File Update Notifications

It happens either when:

* someone updates a file in a group conversation you belong to 
* someone comments on a file in a group conversation you belong to  
* someone uploads a file in a group conversation you belong to
* someone deletes a file in a folder you belong to 
* someone renames a file in a folder you belong to 

A File Update:

* changes the icon to Unread
* If file update is 'New File', changes the icon to green unread. 




# Work in Progress

Read this section to know what's coming.


### Connected, Active

Users should receive alert notifications only when they haven't been paying attention.

Suggestion:
 
`user connected in desktop` User has Eyja Desktop open and has used Eyja in the past X number of minutes.  
`user connected in web` User has Eyja Web open.  
`user connected in mobile` User has the app in the foreground.  
`connected user` User is active in desktop, web, or mobile.   

`active conversation` A conversation that the user has in front of her.

If a user is connected to the desktop but she is not on the active conversation where the tag happens, sound and desktop notifications are triggered.  
If a user is connected to the web but she is not on the active conversation where the tag happens, sound and desktop notifications are triggered.   
If a user is not connected on desktop, tag notifications are pushed to the phone.  
If a user is not connected, tag notifications are sent by email.   


### Notification Settings

Users should decide what is relevant for them:

* at the app level
* at the group conversation level
* at the conversation level

Suggestions

Provide notification preferences at all levels. Preferences may be:

* tag notifications for tag and direct messages
* tag notifications for any update
* mute

Also, for each preference, users might want a different notification preference for mobile. 



### Hot Key

Users should be able to use Eyja with their keyboard only.

Suggestion (OSX):  
Search - CMD + F  
New Conversation - CMD + N  
Conversation Toggling - CMD + down/up  


### Deletion

When a user deletes a file in Eyja: 

* file update 'file deleted' in both the file thread and the file conversation
* file disappears from the group conversation tree
* file   

`Delete Conversation`. The conversation is removed from all ascendant conversations and the conversation is archived. No notification in ascendant conversation appears.  

### Move

* someone moves a file you uploaded
* someone moves a file you edited
* someone moves a file you commented on
* someone moves a file in a folder you belong to 



### File Updates in Group Conversations

File Updates bubble up to the ascendant conversations.  
WIP: what we show in file conversations.


### Name Conflict

Name conflict with first and last names. 
Conversation names are unique among the same level.  
File names are unique among the same level.


### Group Conversation with Two Members

A group conversation can be started by two members. If it has a unique name, it creates a new conversation. If it does not, it links back to the direct covnersation. 

### Roles

* Non-admin: can join public conversations, invite people, create new conversations, delete conversations, same for files.
* Admin: non-admin rights + can invite new users to Eyja, can delete users, can set things for the entire Eyja organization (data retention policy, 2FA).

### Transitioning AeroFS to Eyja

* start clean. have people move their aerofs folder, and keep the folder they want back to Eyja.
* all members need to be owners of the shared folder


### Banners, Alerts, Push Notifications, Emails

When someone is tagged:

* OSX: if the user has Eyja open in OSX, a Banner notification is displayed in the notification center. The user can respond by hitting a 'Reply' in the Banner to send a message back. A sound is heard.
* Windows: if the user has Eyja open in Windows, a notification appears next to the user tray. A sound is heard.
* Mobile: push notification. 
* If a user has Eyja open on desktop or web, she should not receive a push notification on mobile. 
* If a user has Eyja closed in all devices, she should receive an email with the message.


### Notification Clearing

A Notification is cleared when:

* For tag notification, when the user opens the conversation where the tag happened.
* For unread notification, when the user opens any of the conversations where the update is displayed. In particular, notifications that were triggered by file updates disappear upon clicking. 

### File Manipulation in Desktop

If a file is dragged to a Shared Folder or a subfolder, the file is synced. 

`Move File`. A user can move a file from 


### Private Folders
There should be a way to sync the files.

### Naming

* Camel Case enforcing for group conversations
* Group conversations names are unique
* Files in folders are unique

### Selective Sync

Allows users to stay in a conversation but not sync the files themselves. Every time they download a file, they will have to download the file. If they make edits to the files, they will need to upload it again.  
Suggestion: Dropbox allows new uploads with the same file name to replace the file in place. 


### File and Conversation Linking


### Search

### UX at scale
Scale 1,000 (users, groups, files, directories)

Folder / Fike Limits 

### Archiving

### Sign Up / Invitation

### Admin

### Experience without a desktop.
* Users might try open a file on web or mobile, and reupload in the same conversation to 'sync them'. AeroFS currently does not support file syncing through uploading a file with the same name. We should think supporting it eventually.

### AeroFS desktop interface. 
* It needs a revamping to feel it is part of the Eyja product. For now we can live with using the existing interface and linking back to Eyja in some way. Example: 'Share This Folder' keeps the same old look. Once a user invites people, the notification might say to the user 'click to open the conversation in eyja'. 

### Reminders on messages
Ability to add `add a reminder` to a message and get reminded

### Unread Inbox


### Integrations

* gerrit
* JIRA
* Box

### Personal Folders

There should be a way in Eyja to access files from private folders.


### Reactions



# Stuff

Where I keep design problems, thoughts, and notes.

### Open Questions

* if I delete a file, how does that work?
* if I move a folder out, how does that work?
* if I rename a folder, how does that work?
* If I delete a folder, how does that work?
* if I delete a conversation, how does that work?
* if I kick out someone, how does that work?
* should conversations be public by default?
* how do we make sure that people to expose private information in public channels?
* how do we make people understand their plan?
* how do you display ad hoc groups from well-defined groups?
* how does the online/offline status work?

#### Thoughts on Types of Conversations and Files

`Active conversations with active files.`  
e.g. Board Meeting 
Works GREAT

`Conversations with file updates, but no messages.`  
e.g. Air Computing Pictures  
Conversation-level notifications  

`Conversations with no file updates.`  
e.g. Strictly Curated  
Watch for accidental syncing. Group conversation should allow to force (or use by default) Send a File

`Only one file update is relevant.`  
e.g. AeroFS Team/Recruiting/Engineering  
Weak. User will spend a lot of times expanding the tree to find the tree. 
Solution: show the unread without expanding. 
Solution 2: show an inbox.
Solution 3: ability to subscribe and unsubscribe to a folder or a file.

`Messages or files updates that are relevant for multiple group conversations.`  
e.g. Engineers wants to access pictures for a project (or receive file updates) that are in the design folder.  
Solution: make named group conversations public, and add the ability to link them.   People who need to read the file updates need to join the group conversation.  
Solution 2: ability to subscribe to a folder or a file.   

`Ability to subscribe to a folder or a file.`
* should still support the canonical folderd of a conversation. 
* visual distinction between canonical folder and subscribed folder.  
* watch for loss of information. e.g. there is a design comment about a file not in the file conversation, but in the group conversation. because the engineer subscribed to a subfolder, the conversation is missed. 


### Things Users Want To Know (notifications)

Things that are relevant:

#### someone updates a file
someone updates a file you uploaded  
someone updates a file you edited  
someone updates a file where you left a comment  
someone updates a file in a group conversation you belong to  

#### someone comments on a file
someone comments on a file where you left a comment  
someone comments on a file you uploaded  
someone comments on a file you edited
someone comments on a file in a group conversation you belong to  

#### someone sends a message
someone sends a message in a group conversation you belong to 
someone sends a message to you  

#### someone uploads a file 
someone uploads a file in a group conversation you belong to

#### someone deletes the file 
someone deletes a file you uploaded
someone deletes a file you edited
someone deletes a file you commented on
someone deletes a file in a folder you belong to 

#### someone renames a file  
someone renames a file you uploaded
someone renames a file you edited
someone renames a file you commented on
someone renames a file in a folder you belong to 

#### someone tags you
someone tags you

#### someone moves  
someone moves in a group conversation  
someone moves out from a conversation  

#### someone modifies a message
someone modifies a message by editing it  
someone modifies a message by deleting it   



Files uploade for file sending.


creating add-hoc conversations
A member can add or remove a user; however this means a new conversation is created with the new set of members (except if the conversation with the same members exist; in that case it will take the user to the new conversation). 




Unicity:
We could break immutability, but Compose a message will change. There 2 groups 'A, B, C'. A invites B and C to a new conversation. Now there are 3 groups 'A, B, C'.

in 1:1 conversation 'A, B':

- 'A' invites 'C' 
	- if 'A, B, C' exists, it goes to the existing conversation.
	- if 'A, B, C' does not exist, it asks 'A' 'do you want to keep history for 'C'?' Then it creates a new conversation. 'A, B, C'. 'B' sees that its messages with 'A' were shared with 'C'. While 'A' thought it was OK, 'B' thinks it isn't.

- 'C' leaves. 
	- if 'A, B' exists, not sure of what happens. Maybe 2 groups of 'A, B'? 
	- if 'A, B' does not exist, the conversation becomes 'A, B'. This is a 1:1 conversation, but 'C' is shown in the history. 'A' asks herself 'If 'C' was in the conversation, does this mean my messages be shared with someone else?'




