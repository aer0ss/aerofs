# Manual Testing for Eyja

## Main Goal:

- Make sure Eyja works as expected

## Goals:

- Validate Mac installer packaging - properly signed
- Validate Mac repackaging - configuration
- Validate Windows installer packaging - properly signed
- Validate Windows repackaging - configuration
- Validate iOS app
- Validate Android app
- Validate web app
- Validate auth code sign in
- Validate javascript update in Eyja apps with appliance update
- Validate current functionality of messaging and file share with Eyja
    - Create direct conversation
    - Add/remove people from channels
    - Notification when tagged
    - Notification for unread message
    - Create channel
    - File upload
    - Share AeroFS links
    - Edit profile
    - Add avatar

## Long-term Goals:

- Convert all manual tests to automated ones

## Non-Goals:

- Testing the appliance
- Browser compatibility
- UI/UX except for sign-in flow

## Functional tests:

### Pre-Conditions:

- New appliance built and tested for release

## Pass 1

Test fresh install

#### Setup

- Launch and configure new appliance

#### Client and app installation

- Download Eyja desktop app on Windows, Mac, Linux (3 separate accounts)
- Download iOS and Android Apps (2 of the accounts)

#### Sign in

- Verify email input successful
- Verify email sent with auth code
- Verify successful sign in with auth code

#### Messaging

- Verify direct convos send and receive on the above platforms
- Verify create new channel succeeds
- Add/remove people from channels
- Verify channel messages send and receive on the above platforms
- Verify notification when tagged
- Verify notification for unread message

#### File upload and shared links

- Verify uploading/viewing a file succeeds
- Verify sending/viewing a shared link

#### Profile

- Verify a successful profile edit
- Verify a successful avatar change

## Pass 2

Test upgrading Eyja with an appliance upgrade

Note: these manual tests can be done concurrently with the appliance upgrade testing for
efficiency.

#### Setup

- Launch and configure an appliance with the previous version
- Install desktop clients (Windows, Mac, Linux) and iOS/Android apps using previous version of the
  appliance
- Create direct conversation
- Create/send message in channel
- Download a backup file from the appliance and take a screenshot of the appliance console
- Launch the latest version of the appliance and configure by restoring from the backup file

#### Features

- Verify that the desktop and iOS/Android apps update (new javascript should be pulled from the
  appliance)
- Verify that the existing conversations (direct and channel) remain after the update
- Verify direct convos send and receive on all the platforms
- Verify create new channel succeeds
- Verify send and receive message in channel on all the platforms
- Verify uploading/viewing a file succeeds
- Verify sending/viewing a shared link
- Verify a successful profile edit
- Verify a successful avatar change
- Verify user tagging sends a notification
