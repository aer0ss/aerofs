# Centralized Policy Management

Revision history:

>v1: 03/20/2013  Initial draft. Capture discussion notes from Eric, Drew.
>
>v2: 03/22/2013  Update with review notes from Drew.
>
>v3: 03/26/2013  Update with review notes from Eric. Added section on
>syncignore, reworded sample UI text.
>
>v4: 03/29/2013  Moved to repository.

## Goals

Immediate goal: unblock users with very large files who are concerned about
storage of versions. This includes users with existing backup infrastructure.
They want sync history disabled, period.

Medium goal: Give business users (and others) better clarity and control over how
much space AeroFS will require. 

Or, put another way, business users don't like it when our space requirements
grow and they can't figure out why. This is especially true when using block storage,
as the user does not have familiar tools available to figure this out.

This design is one component towards that long-term vision. We still need tools 
that facilitate storage requirement forecasting. How much storage am I using now?
How much is this thing growing? How much growth can be attributes to new content
vs versions vs ???

Do this in a way that we don't regret. Once you ship a feature, it's hard (imho
impossible) to unship it. So don't screw this up: design policy limits that
overlay nicely and don't conflict with each other. Do the bare minimum to MEET
customer expectations.


## Approach

This work can be tackled in three major chunks:

- client/Team Server able to follow the configured policies;

- Admin able to centrally configure policies for an Organization/Team/User;

- UI/client able to view and set policies for their own devices (whether 
using client UI or web UI)

_TODO: Currently I'm assuming the centralized admin is the priority. Is that true?_


## Requirements

_TODO: validate priorities for the following_

The following requirements are first-order:

- Users and Team Server admins will be able to turn off sync history storage
outright. (Needed for immediate business prospects).

- Team Server admins will be able to view and set overall storage limits for version
history on the Team Server.

- Business admins will be able to view and set storage limits for sync history 
for an organization or team, from a centralized interface.

- We provide a simple user interface to view and control storage limits in all the above 
cases.


The following requirements are interesting in the long-term, but not currently scheduled:

- Users will be able to view and set overall storage limits for sync history on their
devices.

- Users will be able to turn off sync history for specific file types. Admins will
have this ability for a team or organization.

- Users will be able to turn off sync for specific file types. Admins will have this
ability for a team or organization.


## Policy Management flow

The normal workflow is as follows. (Box and arrow diagram to come)

- Administrator creates or updates a policy on the Web UI.

- When they commit through the UI, it writes the updates through to a backing database
[ _vigorous hand-waving_ ] which updates the centralized Configuration Server.

- The Configuration Server initiates notifications for the affected users and devices
through lipwig. The notification just indicates "You need to check for updated configs".

- On next device startup, or in response to the message from verkehr, the device
gets the new or updated configuration values from the centralized Config Server; the
values are assumed to be cached locally.

- Within the AeroFS device, the Configuration Client package has a default/override
mechanism. The config code will present the internals with a single value, taking into
account the values set in the Java code, the JVM system properties, a local configuration
file, and the Config Server instance. 

Once the client code is updated to watch the Arrow config service, the backend support
for the Config Server is orthogonal work.


## History Storage Limits

For simplicity, we attempt to cast all user configuration in a single axis.
For example, an axis of "delete versions when: ". We want to avoid combinations of
options that are conflicting or undecidable. (_"Keep at least 10 but never more than
50 Mb" - so what do I do with this 2GB file??_)

>__eric:__ I agree, but not 100%. I don't see any reason to stop an
>administrator from defining complex policies like this... however I also don't
>see any reason to support them now (or ever). I would not design a system that
>DID NOT allow multiple attributes to be combined into a complex policy.

The following sample UI panel shows the storage limit configuration values for the first
implementation:

        Whenever AeroFS syncs one of your files to this device, it will make a
        local copy of the old version. You can view your stored file history by
        clicking [ here ].

        To limit the space used by file history, we can automatically delete old
        versions.

        AeroFS should automatically delete old versions of a file:

        [ x ] never delete old versions.
        [   ] always; keep no sync history.
        [   ] when more than _______ versions have been stored.
        [   ] when all my old versions take up more than _____ [MB/GB/% of disk].

        Note: Disabling sync history means you will not be able to undelete
        files from the AeroFS client!

The same options should apply to the end-user client as to the Team Server.

The centralized administrator version of the above panel provides the same options,
but with amended wording.

The important distinction for centralized management is that the admin must first
select the Organization, the Team, or to a User. If not explicitly specified
for a User or Team, the policies are inherited from the larger unit.

When updating a policy for an Organization, if Team- or User-specific policies exist,
the administrator must be asked whether to override the more specific policies.

		You have updated an Organization-wide policy, but you have one or more
		Users or Teams that have custom policies. Do you want to set this
		value globally, or as a new default?
		
		If you want this new setting to apply globally, we can remove the
		custom policy values you have defined for Teams and Users.
		
		Or you can set this Organization policy as the new default - it will
		only affect Users and Teams that do not have a custom policy.
		
		[  Cancel  ]  [  Apply Globally ]   [  Default Policy  ]


##.syncignore

Goal: help organizations that have existing backup infrastructure to avoid
wasting IT resources. For instance, imagine a designer that already has
automated nightly backup for PSD files.

### Feature requirements

Give users the ability to set synchronization and history policy on a
per-store basis. (Think of this like `.gitignore`.)

Allow the user to provide:

- a set of file patterns that will not be synced. Examples: `*.swp`,
`desktop.ini`, `Thumbs.db`.

- a set of file patterns that will not use sync history. Examples: `*.psd`.

For business users, allow an administrator to set up policies that apply to
all the team members in an organization.

A possible enhancement to the above is to allow the ignore-lists to be set per
subdirectory. (Is this fine-grained approach useful? Could use it to
disable sync or history altogether for a subset of the AeroFS folder
rather than by filename)


### User interface

This feature could be useful to personal-plan clients as well as team admins.
Once the ignore feature is in place on the client/team server, we can decide
how/whether to expose it for each class of user.

> Prototype: 

The simplest implementation, but worst user experience, is to check for a named
file (`.syncignore` and `.historyignore`) in the root synced directory.
Each file, if it exists, is expected to contain a list of filename patterns. 
We only need to support "*" wildcard matching, no need for fancy regexps.

This is really just enough for us to test the feature.

> Client UI

Add a button to the Preferences panel:

	[ File Types ]
		
This leads to a dialog:

		Normally, AeroFS will sync everything from your AeroFS folder
		to all your devices.  You can create special rules for specific
		file types here.
		
		Note that rules created here will apply to your files on all
		your synced devices.
		
		File Types          Rules
		+----         ---+  +----  ----+
		|  Thumbs.db     |  |          |
		|  desktop.ini   |  |          |
		|  *.psd         |  |          |
        +-----        ---+  +----  ----+
        [+] [-]

Choosing a file type on the chooser on the left shows the rule editor for that
file type on the right panel.

The rule editor can be enhanced as we come up with more policies.

	Rules
	
    	[ x ]   Sync this file type
    	[ x ]   Keep sync history for this file type
    
By default, both are checked when a new file type is created.
    
Note that it becomes difficult to present an understandable UI once subdirectory
ignore-lists are allowed. Propose keeping that out of the UI even if fine-grained
policies are implemented later.

> Team configuration

An administrator must be able to configure policies on behalf of a user,
a team, or an entire organization. This should be through a Web UI.
The mechanism is more or less as described for the Client UI; create a file type pattern,
then choose the rule values for that file type.

To be expanded as we work on the web UI.

If a user is on a business plan, the client UI should show the policies inherited
from the team/organization values set by an admin. The inherited policies should
be visible but not editable.

##Futures

Deletion policies (delete oldest? Or Time Machine-style collapse intermediates?)
is orthogonal to the above, in a good way. We can add separate options for how
often to check for policies, which versions to delete, etc.

