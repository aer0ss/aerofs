# Eyja Customer Analytics

## Objective

Tweak the existing Private Cloud Customer Analytics project to collect Eyja-centric events.

#### KPI Metrics

* Global trends of:
	- Total number of users
	- Total active users
	- Total number of messages
	- Total amount of data storage

#### Sales Requirements

* Snapshot of a particular customer
	- Number of users
	- Number of active users
	- Number of messages
	- Amount of data stored

#### Marketing Requirements

**_NB:_** The marketing requirements may change, pending feedback.

* User-level information
	- Number of messages sent by userID
	- Amount of storage used by userID
	- Number of channels created by userID

#### Product Requirements

* Product usage information
 	- Number of file posts
 	- Number of file syncs
 	- Number of channels
 	- Max/average number of files and subfolders per conversation
 	- Number of times users marked all notifications as read using the notifications inbox
 	- Number of times users 'left' a channel
 	- Number of times users 'muted' a channel

 **_NB:_** Do we need product data points in V1 of analytics? We could invest the time to track all
these things, but there is a good chance that some of them will change and then the data point may
become obsolete.


## Events

### Real-time Events

* New user signup (by userID)
* Create channel (by userID)
* Create group DC (by userID)
* Channels & conversations search (by userID)
* Mark all notifications as read via notifications inbox (by userID)
* Message/file content search (by userID)
* Leave channel (by userID)
* Mute channel (by userID)


### Bi-hourly Events
* Messages sent (by userID)
* File syncs (by userID)
* File posts (by userID)
* Size of files uploaded (by userID)


### Daily Events
* Total number of users (by team)
* Number of active users (by team)
* Total number of messages (by team)
* Total data storage (by team)
* Total number of channels (by team)
* Max number of files in a conversation - a.k.a shared folder (by team)
* Average number of files in a conversation (by team)
* Max number of subfolders in a conversation (by team)
* Average number of subfolders in a conversation (by team)
