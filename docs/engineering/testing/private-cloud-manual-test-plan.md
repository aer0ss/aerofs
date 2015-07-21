# Private Cloud - Test plan

Manual testing for release candidates

Goals:

 - a safety net for private cloud.


 - validate appliance format & internals w/ vmware and VirtualBox
 - validate Mac installer repackaging - properly signed
 - validate Mac repackaging - configuration & cert
 - validate Windows installer repackaging - properly signed
 - validate Windows repackaging - configuration & cert

 - validate LDAP setup
	- web signin works
	- verification servlet works

 - validate SMTP setup
 	- local smtp works
 	- external smtp works
 	- verification servlet works

 - validate network configurator

 - backup process works
 - restore-from-backup works

Other goals:

 - Keep manual testing time to an hour or less

Non-goals:

 - browser compatibility
 - functional (sync) testing
 - UI/UX except for setup flow
 - anything handled by existing testing (CI)
 
Long term:

 - convert all manual tests to automated ones

## Appliance

### general sanity

 - name follows standard (currently aerofs-appliance-0.x.y.ova)

### vmware

 - Import OVA works.

### virtualbox

 - import ova works.

## Functional tests

Pre-conditions:

 - keep a backup file from a previous release
 - OpenDS server with TLS and known certificate

### network configurator

 - set static ip
 - set static dns
 - reboot

### Setup

Doesn't matter which virtualization environment we use here.

 - accept license file

 - name machine

### Pass 1

- [configure TLS LDAP](OpenDJ-LDAP-Setup.html)
    IP, bind DN, certificate info
    (an OpenDS server is probably a-ok)

 - configure external SMTP (what should we use? sendgrid? svmail?)

 - complete setup

 - log in to website

 - download and install Team Server on OSX

 - download and install on Windows 7 vm

 - log in w/ OSX client

 - at least one file syncs

### Pass 2

Re-configure the appliance to try password auth and the re-configure step.

 - re-configure appliance
    - local account management
    - internal smtp
 - log in to web as admin user
    - reset password
 - generate backup file

### Pass 3

Restore from old backup

 - import new appliance
 - use known backup file from an ooold version
 - use defaults
 - sign in as any user.

### Pass 4

Test new backup

 - import new appliance
 - use backup file from pass 2
 - use defaults
 - sign in as any user.

## Tests to be automated

These manual tests are not done on every appliance release (they're costly). We record them here to remember all the candidates for automation.

- All functions in the Bunker Web UI
- All functions in the Web UI
- OpenID
- OAuth
- 3rd-party applications & the user-facing OAuth flow for external apps
- Dryad

