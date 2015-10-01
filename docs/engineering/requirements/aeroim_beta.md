Beta Requirements - User
===

Web, desktop, iOS, Android. 

Functions in **bold** are missing, incomplete, or do not work as expected.



# Functions

As a AeroIM user, I can:

### Send

1. message one person. 
2. message multiple people.
3. send a link.
4. **select and send a file.**
5. select and send an emoji.
6. **tag someone.**

### Read

1. read a message with:
	1. content,
	2. sender's identity,
	3. date and time it was sent.
2. access all previous messages.
3. access all past conversations.
4. access all contacts in the organization.
5. **tell if a receiver is typing.**
6. **tell if the receiver has read the message.** 
7. **preview a file by:**
	1. **seeing the title of a file**
	2. **previewing the file, if possible**
	3. **opening**
	4. **downloading a file.**
8. preview a link by:
	1. link URL,
	2. previewing the page,
	3. clicking on the link.
9. see the emoji.
10. tag someone.
11. **know who has AeroIM open at the time.**
12. know who is part of a group conversation.
13. **know if someone was invited into a group.**
14. **know if someone joined a public group.**
14. **know if someone was removed from a group.**
15. **know if someone left a group.**
13. know the contact's email address.

### Be Notified

1. **receive a notification when:**
	1. **(a new message is sent to me, or**
	2. **a message is sent to a private group I am part of), and**
	3. **the user is not actively looking at the conversation where the message appears.** 
2. **receive a notification when I am tagged in a public group conversation.**
3. receive a notification when I join a conversation.
4. receive a notification when I leave a conversation.

### Join, Invite and Remove

1. make a group conversation accessible to anyone who wants to join.
2. find all public conversations.
3. join a public conversation.
4. add someone to a conversation.
5. remove someone from a conversation.


### Search

1. search for any string within all conversations (message, link, file title) and access the conversation.
2. search for any contact and access the contact.

### Archive and Delete

1. hide a conversation. The conversation stays searchable.
2. unhide a conversation.
3. delete a conversation.


# Experience

Some of the requirements need to be described visually to live.

### Least Effort Possible

User should make the least effort possible to (in this order): 

1. contact one person,
2. resume previous conversations,
3. move from a notification to an unread message,
4. identify the unread message from read messages,
5. reply to a message,
6. contact multiple people,
7. add people to conversations,
8. search and access a past conversation.
9. tag a person.


### Delivery Speed

1. once a message has been sent, the receiver should receive the message close to instantly.


### Delivery Reliability

1. **A message that was successfully sent should arrive to the receiver.**
2. **A message that was sent before another message should arrive to the receiver before the second message.**
3. **If the device is offline when the user sends the message: **
	1. **there is a warning message that the device is offline and the message was not sent. **
	2. **the application tries to resend the message automatically.**
	3. **after multiple failures, the application notifies the user the message was not sent. The application provides a way for the user to try to send it again. **

### Privacy

1. **Notifications should display the sender's name.**
2. **When adding a participant to a group, the new participant sees the history of messages.**
3. **When a participant leaves a group, the participant can keep the history of messages.**


### Cross-Platform

1. A notification should be pushed to all platforms the user is not on.
2. **If a message is read on one platform, it should be marked as read on other platforms.**
3. **The look & feel (colors, font, iconography) should be consistent across all platforms.**
4. **The order of messages should be consistent across platforms.**

### Search

1. **Search results are provided with previous and subsequent messages.**

### Platform-specific Optimizations

Desktop:
1. copy paste a screenshot.
iOS:
1. upload a picture from library or camera.


# Exclusion From Beta

1. delete a message.
2. edit a message.
3. drag and drop a file on desktop.
4. Emoji autocomplete.





