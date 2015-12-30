# Private Cloud Test plan

Manual testing for release candidates.

Goals:

- A safety net for private cloud.

- Validate appliance format & internals w/ vmware and VirtualBox
- Validate Mac installer repackaging - properly signed
- Validate Mac repackaging - configuration & cert
- Validate Windows installer repackaging - properly signed
- Validate Windows repackaging - configuration & cert

- Validate SMTP setup
  - Local smtp works
  - External smtp works
  - Verification servlet works

- Validate network configurator

- Backup process works
- Restore-from-backup works

Other goals:

- Keep manual testing time to an hour or less

Non-goals:

- Browser compatibility
- functional (sync) testing
- UI/UX except for setup flow
- Anything handled by existing testing (CI)

Long term:

- Convert all manual tests to automated ones

## Appliance

### General sanity

- Name follows standard.

### vmware

- Import OVA works.

### virtualbox

- Import ova works.

## Functional tests

Pre-conditions:

- Keep a backup file from a previous release
- OpenDS server with TLS and known certificate

### network configurator

- Set static ip
- Set static dns
- Reboot

### Setup

Doesn't matter which virtualization environment we use here.

- Accept license file
- Name machine

### Pass 1

Tests appliance/client/TS upgrade flow from previous version

- Install desktop clients (using two different accounts) and Team Server, using previous version of
  the appliance (i.e. the version prior to the one you are testing)
- Download backup file from this appliance
- Take screenshot of this appliance's console
- Launch latest build and configure static IP and assign same values as the old appliance (use the
  screenshot as a reference)
- Verify static IP and DNS assignments succeed
- Restore from the old appliance's backup file
- Verify that all settings are pre-populated and restore succeeds
- Verify that clients and Team Server are able to upgrade to the latest version
- Verify that file upload from outside the iOS app is successful

#### Pass 2

Tests appliance restore flow from a very old appliance version.

- Launch latest build and restore from a much older backup file (recommend at least 5 release
  versions old)
- Verify restore is successful
- Sign in using any user account

### Pass 3

Test new appliance setup flow using latest build

- Launch latest build and setup as new appliance
- Use internal SMTP relay
- Verify SMTP verification step succeeds
- Verify setup and admin user creation is successful
- Log into web as admin user and reset password
- Log in using new password

### Pass 4

Basic tests that cover major product features. For this test pass, use the same appliance as Pass
3.

#### External Mail Relay

- Enter the **Setup** flow from Appliance Management Interface
- Configure external SMTP (you can use smtp.gmail.com, port 587, and your gmail account creds)
- SMTP verification step succeeds
- Complete setup and log into Web interface

#### Client Installation and File-Sync

- Download and install Team Server on OSX
- Download and install desktop client on Windows 7
- Download and install desktop client on OSX
- Verify at least one file syncs between both clients and Team Server

#### Web Access

- Verify files are accessible via 'My Files'
- Able to successfully create links via Web interface and access links (Be sure to test password
  protected links)
- Able to successfully create links via Desktop interface and access links

#### Generate Backup File

- Download backup file and put appliance in maintenance mode
- Verify the 'Maintenance mode' page appears when the appliance is accessed while in maintenance
  mode
- Exit maintenance mode
- Verify the appliance login page is displayed instead of maintenance mode page
- Take a screenshot of this appliance's console

#### Mobile Apps

- Scan QR code, verify that file download to iOS and Android apps is successful when a new file is
  uploaded via web/desktop
- Verify that file upload from outside the iOS app is successful and appears on desktop/web

#### Pass 5

Tests appliance restore using backup obtained from latest build version.

- Launch latest build
- Assign static IP and DNS using screenshot obtained in Pass 4
- Restore from backup file obtained in Pass 4
- Use pre-populated defaults and verify restore is successful
- Sign in using any user account


## Tests to be automated

These manual tests are not done on every appliance release (they're costly). We record them here to
remember all the candidates for automation.

- All functions in the Bunker Web UI
- All functions in the Web UI
- OpenID
- OAuth
- 3rd-party applications & the user-facing OAuth flow for external apps
- Dryad
