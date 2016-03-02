# Eyja Integrations Architecture

This document outlines the architecture of integrations built into Eyja. The
deadline for these features will be by **April 1, 2016**.

### Integrations
- **Bot User**
    - A bot is a user with the majority of all user permissions. Whether or not
      a bot can join other conversations like a regular user might be worth
      restricting. Like a regular user, they can be invited and kicked out of
      channels and group conversations.
    - A single bot user can be a member of all channels and group conversations.
    - All conversations are able to have a bot user as a member.
    - It is not well defined whether a bot can be tied a specific channel
      on creation or if it global to the entire Eyja instance. For now, all bots
      will be tied to their Eyja instance, enabling them to be added to all
      convos.
    - On bot creation, a generated bot-specific Oauth token is given allowing
      the bot creater acccess to an API for tapping into Sloth push
      notifications.
    - A bot would require tapping into the Sloth API used by the Eyja front end.
      The current API uses websockets for notifications and a REST API for data. Possible
      research might be to see the benefits of using Slack's RTM-api model that
      uses websockets but our current model is fine.
- **Slash Commands**
    - A slash command is a message of the form `/<cmd> <arguments>`. When a user
      types a message prefixed with a slash, a HTTP POST command is sent off to
      a configured server. The server should then respond with a JSON response
      that is converted to a message.
    - Builtins will be provided for common integrations. The list is the
      following:
        - Bonusly
        - Giphy
        - Github
        - JIRA
        - PagerDuty
        - Stripe
        - Zendesk
- **Incoming Webhooks**
    - These will be used to integrate other systems into Eyja. This will expose
      an endpoint to receive HTTP-post requests from other services. Incoming
      webhooks will be restricted to posting messages into a desired
      channel/group conversation.
    - The creation of these webhooks will be done via the web-UI and will expose
      a particular route to be used for that webhook. Each created incoming webhook will be given an OAuth token to make this possible.
- **Outgoing Webhooks**
    - This will allow you to send internal Eyja message data to other services.
    - A message will be sent to a configured outgoing webhook when a message is
      typed into the webhooks configured channel/group conversation OR when a
      message contains a defined trigger word for the webhook. Note that this
      functionality is similar to a slash command.
- **App**
    - The concept of an app is a slash command or webhook that is portable to other Eyja instances.
      This introduces the concept of a global AeroFS app store which is out of scope for next month.

## Integration Similarities and Differences
- Slash Command, Outgoing webhooks
  - A slash command is trigged on a `/<cmd>` that maps to an existing command.
    An outgoing webhook is triggered on either all messages in a
    channel/groupConversation, or the first word of a prefix `<msg_prefix>
    <rest_of_msg>`. It might be worth inputting regex too.

## Implementation

- **Backend**
    - Sloth is the messaging backend service used to send notifications and is
      the primary Eyja API endpoint. Sloth will be extended to support outgoing
      and incoming webhooks. Sloth handles messages and all hooks either insert
      messages locally or propogate them to another service. Creating a new
      container will just generate redundant inter-container calls.
    - Slash commands currently live in Sloth. Sloth has a configured endpoint to
      retrieve a list of all commands and to also create a new command. This
      endpoint will be used on the frontend page to create a command.
    - Similar endpoints will be used for the creation of webhooks
    - To support bot users, the Sloth API must be exposed. This is similar to
      the RTM-API supported by Slack using websockets.

- **Frontend**
    - Set of webpages for integration creation
      - Slack provides a central integrations page
        <https://aerofs.slack.com/apps/manage/custom-integrations> that lets you
        create and manage slash commands, bots and incoming webhooks. This
        service can be provided by either modifying the default AeroFS web
        entrypoint (<share.syncfs.com>) or by creating another set of webpages
        specific to Eyja and managing integrations. There will not be a public
        API exposed to create integrations.
    - Eyja Messaging
      - <share.syncfs.com/messages> will be modified to incorporate the adding
        of a bot to a channel. There will either be a navigation bar to navigate to pages for integration creation, or it may be implemented on the messaging page.

## Research

- Create an instance of each Slack integration to gain a better understanding of
  the flow they provide. This includes incoming, outgoing webhooks, a slash
  command, a slack bot and an overall slack application to see the package
  things to make them portable.
- Investigate the sloth database schema to see how these would fit (how a bot
  coupled to a group would be represented, is a bot a regular user?, etc.)
