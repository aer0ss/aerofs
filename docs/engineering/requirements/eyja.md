I
Eyja 
====

Latest Update: Apr 8, 2016

### Resources  

* [Eyja Mockups](https://share.aerofs.com/l/67f506b1386a4f56947b619d111c1947#/)



### Key Concepts


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
	

	

### Conversation Privacy

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

### Desktop Shared Folder Locations


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



### Renaming Folders and Channels

	* If a user renames a channel shared folder on her desktop, the channel keeps its current name.
	* If a user renames a channel, every shared folder that still have the same name are renamed with the new name.
	
<br><br>

### Notifications Behavior
	
`Notifications`  
[Notification Design](https://share.aerofs.com/l/5cefa362ebf14d0daf347eba03e3b143) 


<br><br>

### Desktop Deletions

`File Deletion`
When a user deletes a file that belongs to a shared folder: 

	* The file is deleted in every member's shared folder
	* A Message Update 'File deleted' is displayed in the channel 
	* File disappears from the roster
	* File disappears from My Files
	* Users can download the deleted file by clicking on the file update.
	
`Shared Folder Deletion`
When a user deletes a shared folder on the desktop:

	* The user does not leave the conversation.  
	* If the user tries to sync a new file from the conversation ('sync a file' button, or drag and drop), a message states that the user must sync the shared folder again, and prompts for confirmation to sync the shared folder and sync the file.
	* If the user tries to download a file from the conversation, a message suggests to the user that she would have a better experience syncing the shared folder again. The user can dismiss the message, and she can opt in to never seeing the message again. 
	
<br><br>

### Links

Create/Copy a link:

	* If a link with no restrictions exist, copy the link
	* Otherwise, create a new link.
	* A link can be restricted in My Files. (not supporting the case would take more time)
	
<br><br>




# In Review

Section is under discussion. If you need any of the things below to move forward, let's talk. 


<br><br>


### Quotas

`Quota variables`

	* storage quota
	* message retrieval quota
	* apps integration quota
	
If easy, make quotas variables configurable. 

`Storage Quotas`

	* Storage quota is at the team level. Say 2Gb size limit for the free plan.
	* Files that are shared count against the quota only once (no double couting a file shared between 2 people)
	* When team reaches the quota, existing files still sync, unless new versions exceed the storage limit. New files cannot be uploaded until upgrading to the paid tier. 

`Message Search Quotas`

	* Message quota is a team level.Say up to 10,000 messages searchable.

`Apps Integration Quotas`

	* Third-party Apps are at the team level.
	* When team reaches the quota, existing third-party apps integrations still work. Users cannot add a new third-party app until upgrading. 
	

	
	
### Product Variants from AeroFS

`Remove`
remove it from UI and code

`Hide` 
hide it from UI, keep it in code

Right-Click Desktop  
 
* Replace: 'Shared Folder...' with 'Share on Eyja...'
* Add: 'View Conversation' (shared folders)
	
Menu Bar

* Remove: 'Manage Shared Folder'

My Files Non-Admin

_The left menu is removed_

* Replace: Folder/File > 'Actions' > 'Share Folder' with 'Share on Eyja'. Link goes to Create a New Channel page in /messages
* Replace: Folder/File > 'Actions' > 'Manage Sharing' destination. Link goes to the conversation in /messages
* Replace: 'Install' with 'Download Eyja'
* Add: Folder/File > 'Actions' > 'View Conversation'
* Hide: 'Pending Invitations' (we might need when people want to control what is downloaded)
* Hide: 'My Organization' > 'Groups' (we will need once we add Groups to UX)
* Hide: 'Manage Shared Folders'
* Move: 'My devices' (screen should be moved to settings)
* Move: 'Invite a Team Member to AeroFS' (should be in header)
* Remove: Footer
* Remove: 'My Organization' > 'Users' (for non-admins)
* Remove: 'My apps'
* Remove: 'API Access Tokens'
* Remove: 'You have been an AeroFS member since...'

My Files Admin

* Same edits as non-admin, except left menu is kept. 
* My Organization is a separate page. There is a 'My organization' link in the name dropdown (top-right). The link goes to Users page
* Add: left menu: Billing
* Hide: Groups
* Hide: Shared folders
* Hide: Team servers

Appliance Admin

Bunker won't exist, but here are some features we might enable later: 
 
* Customization
* Mobile Device Management
* Identity
* Email Integration
* New User Invitation
* Link Sharing
* Password Restrictions
* System Status



Brand

* Replace: All references to 'AeroFS' (not ready)
* Replace: New styling (not ready)




# Do not cross beyond this point  


This is a repository for future product improvements or incomplete requirements.

### Email Notification

	* User is offline and receives a message on Eyja. User receives an email mentioned it is offline. 
	
<br><br> 
	
### Better Read State

If the user reads a message, don't make her read it a second time somewhere else. This limitation can happen when:

	* a push notification is sent to mobile, recipient reads the message on desktop. The push notification should disappear on mobile. 
	* an email notification is sent to mobile, recipent reads the message on desktop
<br><br>



### Hot Key

Users should be able to use Eyja with their keyboard only.

Suggestion (OSX):  
Search - CMD + f 
Filter Conversations - option + CMD + f
New Conversation - CMD + n
Quick Switcher - CMD + k
Conversation Toggling - CMD + down/up  

<br><br>



### Admin Role

In addition to everything a user can do, an admin can:

	* invite new users
	* delete users
	* set them as admin
	
	
<br><br>



### Mac Desktop Notifications

When someone is tagged:

	* OSX: if the user has Eyja open in OSX, a Banner notification is displayed in the notification center. The user can respond by hitting a 'Reply' in the Banner to send a message back. A sound is heard.

<br><br>



### Selective Sync

Allows users to stay in a conversation but not sync the files themselves. Every time they download a file, they will have to download the file. If they make edits to the files, they will need to upload it again.  
Suggestion: Dropbox allows new uploads with the same file name to replace the file in place. 


### Channel Linking

User can reference a channel #Art Club. Users can click on the channel to go directly in this channel.


### Search



### UX at scale
Scale 1,000 (users, groups, files, directories)

Folder / File Limits 

### Archiving

### Sign Up / Invitation

### Admin

### Renaming Group Conversation Folders

should throw an error that it cannot be done. 


### AeroFS desktop interface. 
* It needs a revamping to feel it is part of the Eyja product. For now we can live with using the existing interface and linking back to Eyja in some way. Example: 'Share This Folder' keeps the same old look. Once a user invites people, the notification might say to the user 'click to open the conversation in eyja'. 

### Unread Inbox


### Integrations

* gerrit
* JIRA
* Box


### Reactions

### External Sharing

* single-channel sharing (free user)


### Stuff

Where I keep design problems, thoughts, and notes.

### Open Questions

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

