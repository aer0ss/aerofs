Beta Requirements - User
===

Web, desktop, iOS, Android.

Functions in **bold** are missing, incomplete, or do not work as expected.

# Functions

As a AeroIM user, I can:

### Send

1. Message one person.
2. Message multiple people.
3. Send a link.
4. **Select and send a file.**
5. Select and send an emoji.
6. **Tag someone.**

### Read

1. Read a message with:
	1. Content,
	2. Sender's identity,
	3. Date and time it was sent.
2. Access all previous messages.
3. Access all past conversations.
4. Access all contacts in the organization.
5. **Tell if a receiver is typing.**
6. **Tell if the receiver has read the message.**
7. **Preview a file by:**
	1. **Seeing the title of a file**
	2. **Previewing the file, if possible**
	3. **Opening**
	4. **Downloading a file.**
8. Preview a link by:
	1. Link URL,
	2. Previewing the page,
	3. Clicking on the link.
9. See the emoji.
10. Tag someone.
11. **Know who has AeroIM open at the time.**
12. Lnow who is part of a group conversation.
13. **Know if someone was invited into a group.**
14. **Know if someone joined a public group.**
14. **Know if someone was removed from a group.**
15. **Know if someone left a group.**
13. Know the contact's email address.

### Be Notified

1. **Receive a notification when:**
	1. **(A new message is sent to me, or**
	2. **A message is sent to a private group I am part of), and**
	3. **The user is not actively looking at the conversation where the message appears.**
2. **Teceive a notification when I am tagged in a public group conversation.**
3. Receive a notification when I join a conversation.
4. Receive a notification when I leave a conversation.

### Join, Invite and Remove

1. Make a group conversation accessible to anyone who wants to join.
2. Find all public conversations.
3. Join a public conversation.
4. Add someone to a conversation.
5. Remove someone from a conversation.

### Search

1. Search for any string within all conversations (message, link, file title) and access the conversation.
2. Search for any contact and access the contact.

### Archive and Delete

1. Hide a conversation. The conversation stays searchable.
2. Unhide a conversation.
3. Delete a conversation.

# Experience

Some of the requirements need to be described visually to live.

### Least Effort Possible

User should make the least effort possible to (in this order):

1. Contact one person,
2. Resume previous conversations,
3. Move from a notification to an unread message,
4. Identify the unread message from read messages,
5. Reply to a message,
6. Contact multiple people,
7. Add people to conversations,
8. Search and access a past conversation.
9. Tag a person.

### Delivery Speed

1. Once a message has been sent, the receiver should receive the message close to instantly.

### Delivery Reliability

1. **A message that was successfully sent should arrive to the receiver.**
2. **A message that was sent before another message should arrive to the receiver before the second message.**
3. **If the device is offline when the user sends the message:**
	1. **There is a warning message that the device is offline and the message was not sent.**
	2. **The application tries to resend the message automatically.**
	3. **After multiple failures, the application notifies the user the message was not sent. The application provides a way for the user to try to send it again.**

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

#### Desktop

1. copy paste a screenshot.

#### iOS

1. upload a picture from library or camera.

# Exclusion From Beta

1. Delete a message.
2. Edit a message.
3. Drag and drop a file on desktop.
4. Emoji autocomplete.
