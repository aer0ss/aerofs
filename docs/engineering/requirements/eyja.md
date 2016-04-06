
Eyja 
====

Latest Update: Apr 5, 2016

Resources  
_____

* [Eyja Mockups](https://share.aerofs.com/l/67f506b1386a4f56947b619d111c1947#/)



Key Concepts
_____


`Channel` A conversation between multiple people, with a name. Channels:

	* must have a name, given by the user.
	* can be either public or private.
	* if public, can be joined, or left by anyone. 
	* if private, cannot be found or joined by non-members.
	* members can add or remove other people.
use case: the channel 'Marketing' that talks about anything under marketing. People from outside marketing might want to join and leave as they please.  
<br><br>
	
`Direct Conversation` A conversation between multiple people, with no special name. Direct Conversations:

	* do not have a special name. Instead, the name is made of the first names of each member, ordered alphabetically.
	* are private.
	* cannot be found or joined by non-members.
	* members cannot add or remove other people. 
	* once created, no member can leave, join, invite, or expel. ('immutable')
use case: a quick conversation between 'Karen, John, Hugues, Yuri'
<br><br>

`File Conversation` A conversation around a file. File conversations:

	* must belong to a channel or a direct conversation.
	* inherit the public or private property of their root channel or direct conversation. 	

use case: a marketing presentation being prepared by multiple people.
<br><br>

`Conversation Subfolder` A folder used to organize contents inside a conversation. Subfolders: 

	* must belong to a channel or a direct conversation.
	* may contain file conversations and conversation subfolders. 
	* do not have any conversation associated with it. 

use case: the subfolder 'Presentations' in Marketing Channel
<br><br>

`Post a File` Uploading a file to a conversation. Members can:

	* preview the file (if previewable)
	* download the file
	* (subject to change in API) replace the file by upload a new version with the same name.

`Sync a File` Uploading a file to a conversation and a shared folders. Members can:

	* preview the file
	* open the file (if viewing from desktop, otherwise: web = download, mobile = open in other apps)
	* get notified for new file changes
	* receive a new copy of the file in their shared folder every time it changes. 


`Conversation Shared Folder`. A shared folder that contains and syncs all the files and folders contained in the conversation. 

	* Members auto-join shared folders.

`Roster`. The list of channels and direct conversations a members belongs to. The roster:

	* displays all channels, ordered alphabetically.
	* displays the 15 most recent direct conversations, order alphabetically. 
<br><br>
	

	

Conversation Privacy
____

`Public Channel`:

	* Can be joined by non-members
	* Messages, file conversations, and files:
		* can be seen by non-members.
		* can be searched by non-members.
	
`Private Channel`:

	* Cannot be joined by non-members. 
	* Messages, file conversations, and files:
		* cannot be seen by non-members.
		* cannot be searched by non-members.
		
Direct Conversations are private.

`Making a Public Channel Private`

* Only applies to Public Channels.
* A member of a public channel can make a public channel private. 
* All other change is disallowed (making a private channel public, making a direct conversation public)

`Inviting a New Member in a Private Channel`

* When adding someone to a private channel, the inviter should be offered the option to show the conversation history to the new member. 

<br><br>

Desktop Shared Folder Locations
____

Eyja Location: ~/Eyja  
Eyja Channels -: ~/Eyja/
Eyja Direction Conversations Location: ~/Eyja/Direct Conversations/

e.g. 1  
Group Conversation 'Marketing'  
Folder: ~/Eyja/Marketing  

e.g. 2  
Direct Conversation with 'Yuri Sagalov'
~/Eyja/Direct Conversations/Yuri Sagalov  
<br><br>


Design
____
	
`Notifications`  
[Notification Design](https://share.aerofs.com/l/5cefa362ebf14d0daf347eba03e3b143) 

`Roster`  
[roster design](https://share.aerofs.com/l/c198dce852294857b44fc171be940154)

<br><br>



### Do not cross beyond this point  
____ 

This is a repository for future product improvements or incomplete requirements.

### Connected, Active

Users should receive alert notifications only when they haven't been paying attention, and they should have. 

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
Search - CMD + f 
New Conversation - CMD + n
Quick Switcher - CMD + k
Conversation Toggling - CMD + down/up  


### Deletion

When a user deletes a file in Eyja: 

* file update 'file deleted' in both the file thread and the file conversation
* file disappears from the group conversation tree

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
Conversation only has notification, works as a feed of changes. 

`Conversations with no file updates.`  
e.g. Strictly Curated  
Like a regular chat app.

`Only one file update is relevant.`  
e.g. AeroFS Team/Recruiting/Engineering  
Weak. Some files might get updated but bring noise to the conversation. The relevant file might be multiple levels below. 
Solution: unread inbox
Solution 2: notification settings to turn off entire sections.

`Messages or files updates that are relevant for people that are not members.`  
e.g. Engineers wants to access pictures that are in the design folder.  
Solution: ability to subscribe to a folder or a file. 
Solution 2: link public channels #marketing.
Solution 3: show them in search. 

