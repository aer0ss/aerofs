# Project: Breyttafoss

**breytafoss**, *noun*: A waterfall of changes.

# Motivation and Goals

In the past, people had access to better tools on their work computer than at
home. The SAAS model has broken and inverted this: public cloud vendors are
motivated to provide amazing UX, for free, directly to consumers. The compute
power of the personal device or connected server is no longer relevant; the web
is where the complex experience is built and maintained.

The IT organization is forced to reduce user choice to meet bare minimums in
security and compliance.

1. People at work ask IT for cool things they already know: (We want Basecamp /
Slack / Trello / IM / Gmail / a terabyte of email storage)

2. IT has to say no. They try to block access to all these things.

3. If they fail to block public cloud services: people leak company
information onto public cloud services.

4. If they succeed at restricting user choice: people fall back to lowest
common denominator, which is emailing files.

We propose to build products that displace consumer tools in the workplace.
More generally, we aim to reduce the risk from "Shadow IT" by giving employees
the ability to self-organize around familiar experiences.

## What's a Project Anyway?

This product integrates messaging, content, and activity in a unified "Project"
view.

### Define "Project":

 - A project is an ad-hoc team - a collection of humans.

 - A project has a shared space for content - (a shared folder).

 - A project has an ongoing conversation.

 - A project has a state (currently Active or Archived... more nuance to be
   added later).

In a bit more detail...

 - Projects can be organized by "last updated" date. Last update could be
   content, structure, or conversation.

 - People within a Project have a permission: Viewer, Editor, Owner.

 - We can estimate and report influence in a project. Frequent contributors are
   listed first.

 - People can be tagged with a position within the project. Contributing,
   managing, reviewing, monitoring. This doesn't necessarily convey permission.

 - Projects are unlisted - there is no "directory of projects" that is
   accessible to non-admins.


### Define a "Conversation"

A conversation is an ordered set of messages between two or more people.

A conversation has a reliably-stored history, and should look the same from any
device or view.

Some people will use mobile messaging clients or the web client. Some won't
even use that, and for them, the individual messages in \ the conversation turn
into email messages (single or digest).


### Defining transitions to Project-hood

#### Conversation to Project.

As soon as you upload content to a conversation, it is a project. Any two
people chatting on an Aero service have an implicit shared folder. When they
start putting things in it, that folder can appear on their devices (or in the
web view alone if that's what they prefer).

`TODO: Do we ask them for a project name when they start to provide file
content? If not, what is the project called? "Bob and Jan's shared space"?`

#### Shared folder to Project.

When in the project view on Aero, you should be able to click to send a message
to the participants in that folder. Doing so promotes that folder to a project.

The initial name of the project is that of the shared folder.

#### Email thread to Project.

An email thread is already conceptually equivalent to a Project. A delightful
UX component would be to turn an email thread (from the server let's say) into
a project. It has a number of human participants, attached files turn into
content in a shared folder, and the message content turns easily into a
Conversation.

#### Project to Archived

A project Owner can archive the whole thing. This has the effect of suggesting
to each user in the project that they let Aero clean up the project content on
their devices.

    "The #project-identifier has been archived. The content and conversation
    are securely backed up on a Storage Agent - would you like to remove the synced
    content from your devices?"

#### Project to Team

Promotion of a project to a "team" is really a labeling change. Teams are
semi-permanent, outlasting any given project lifetime. Teams may be listed on a
company-wide directory, whereas projects are unlisted.

Membership in a Team should incorporate a request-and-approval workflow.

## Storing Project Content : Selective Sync++

A project participant chooses which devices should sync a local copy of the
project files. This is a characteristic of each project - "I want the files for
the Blah account on all my devices; I want the Waldo project on my PC  but not
my Macbook Air."

The participant should be able to "push" the content to any of their connected
devices through the central web administration view. This is equivalent in
functionality to going to that device, clicking through to the selective sync
dialog, and adding or removing the check on the folder.


## Email integration - Invited and Full members

### Invitations

People can be invited to a project by email address alone. They may or may not
have an Aero account. Until they establish an account and click through and
accept the project invitation, the person is in the "CC:" state. They will be
able to receive a courtesy copy of project activity via email. Any list of
project participants will include these people (with a visual indication that
they are in this reduced state).

The user-facing materials do not distinguish "invited but not accepted" and
"invited but no Aero account". These users are equivalently CC-only.

Invited-but-not-accepted users can not view or edit Project files until they
accept.


### Email integration for others

An accepted Project member may configure the options for email delivery. They
can choose to receive email for each project update, or a digest form. Email
integration is a reduced experience compared to the online (or desktop) client.
This simple path allows those who prefer the legacy tools to participate, at
least as a viewer.

Each email should include a link with a call to action to "join the
discussion".


### Legacy file-sync concepts

Your AeroFS installation continues to work as it does today.

The AeroFS folder is unchanged. Ideally an icon overlay highlights the folders
that represent Projects (perhaps this is just the existing marker for Shared
Folders).

In the web view, the folders that don't map to "projects" are grouped under a
single "Personal" heading.

The default web view for AeroFS should be the list of  your Projects - each of
which is a link to a project browser - and one high-level link for "Personal
content".


# Messaging Improvements

The overall experience desired for the Messaging component is self-evident.
Users have a strong sense of what a messaging system should feel like.

 - Consistent view of conversations across many devices;
 - Profile information (photo, email, phone number);
 - Nearly-immediate delivery and notification;
 - Reliable archiving of conversations;
 - Integration with local files and assets, including on the mobile platforms;

# Roadmap

Rough ordering:
: **integration**: Messaging and file-sharing integration. Starts with tying
together the identity systems. Conversations implicitly create shared folders.
: **messaging**: Availability of conversations on non-mobile platforms.
: **file tagging**: The ability to refer to a project file in conversation (not
just uploading a new file every time)
: **email**: Send updates to participants that want email notification


At a minimum, the following front-end designs are required:
: **Project browser**: Present a user with their set of Projects. Sort by last
update, show who is working on it and other status information.
: **Project viewer**: Combine the messaging and file-update information into an
activity feed. Show who has worked on the project, recent comments, and access
to a file browser.
: **Project Admin**: Show and edit the list of participants in the project.
Allow editing roles, removing users, canceling invitations. Open to Owners
only. This is equivalent to the current Shared Folder view.
: **Project Preferences**: Show your status in the project. Allow configuration
of email notification - Spammy, Digest, or Never. Show and edit the set of your
devices that should sync the files for this Project.


