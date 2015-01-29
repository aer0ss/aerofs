# Two factor auth

Two factor authentication can protect accounts from a variety of attacks,
including dictionary attacks, password reset vulnerabilities, and others.  IT
organizations are increasingly deploying and requiring two-factor auth on
mobile devices.

AeroFS is a security-focused company, and for us to be taken seriously in that
capacity, we need to provide a way to secure your account beyond just proof of
email ownership.  To that end, we should support two-factor authentication in
both Hybrid Cloud and Private Cloud.

## Admin workflow

### Enabling two-factor for the org
Two-factor auth is enabled or disabled on a per-account basis.
There is no system-wide setting that prevents users from using two-factor auth.

### Resetting a user's second factor (disable second factor requirement)
If a user loses their mobile device, they need some way to regain access to
their account.  Admins should be able to disable two-factor authentication for
user's account so they can log in and reenable it with a new device.

This method should be made available through the user management API.  An audit
event should be generated every time a second factor is reset.

Disabling two-factor authentication for an account generates an email to the
account holder.

### Seeing if users have enabled two-factor auth
We could show a phone icon or something of the sort on the user list page for
users that have two-factor auth enabled.

We can also expose whether the user has two-factor auth enabled as part of the
Sparta User Management API (say, add a field `two_factor_enabled` to the User
object), which will allow orgs to audit for policy compliance.

## User workflow

### Setting up two-factor auth
User will visit `https://<appliance hostname>/settings`, where they can click
on a button that will take them to a two-factor authentication management page.

There, they will enroll by scanning a QR code or entering a secret key into
their cell phone and entering the current one-time password, to prove that they
have successfully configured their device.

They will then receive an email noting that two-factor auth was enabled for
their account, and future logins will require their current security code.

### Logging in, once two-factor auth is enabled
After proving their identity via the normal means (credential, ldap, or
openid), if a user has two-factor auth enabled, they will be further prompted
to enter their current security code.

If for some reason, they cannot retrieve their security code, they may click on
a "Use a recovery code" link, which will allow them to provide one of their
single-use recovery codes rather than their mobile security code.

### Disabling two-factor auth
This should be an option in the two-factor auth settings page.

Admins can do this on the user's behalf through the user management API.

Users should receive an email when two-factor auth is disabled.

### Desktop apps
Desktop apps will prompt for username and password at installation time just as
before.  If the user has two-factor auth enabled, after providing a correct
username and password, they will be prompted for their current security code or
a recovery code.  Once the user inputs that code, the installation flow
proceeds as before.

Once set up, devices will not prompt for second factor, except perhaps for
recertification after certificate expiry (which generally won't happen to
active users).

### Apps (including mobile)
OAuth apps are unaffected because you log in to the website to approve
authorization requests, and the website will require your second factor to log
in.

Mobile apps are unaffected because you log in to the website to generate the
authorization QR code for the mobile.  The website will require the second
factor security code at that login time, but no changes are needed to the
mobile apps.

## Support workflow

### Disabling a user's second factor
AeroFS Support is explicitly **NOT** allowed to change two-factor auth as a matter
of policy and implementation, as it would allow social engineering attacks to
circumvent two-factor auth, and the second factor is meant to be an opt-in
layer to protect against (among others) such attacks.

A user who loses access to their token must be warned in advance that they will
be unable to recover their account without either their authenticating device
or their recovery codes.

## Design notes

We plan to implement the [Time-based One-Time Password (TOTP)
protocol](http://tools.ietf.org/html/rfc6238), the industry standard for
two-factor authentication, which is supported by a variety of two-factor
authentication apps, including:

  * [Google Authenticator](http://support.google.com/accounts/bin/answer.py?hl=en&answer=1066447) (Android/iPhone/BlackBerry)
  * [Duo Mobile](http://guide.duosecurity.com/third-party-accounts) (Android/iPhone)
  * [Amazon AWS MFA](http://www.amazon.com/gp/product/B0061MU68M) (Android)
  * [Authenticator](http://www.windowsphone.com/en-US/apps/021dd79f-0598-e011-986b-78e7d1fa76f8) (Windows Phone 7)

We will store the second-factor secret in the user account database.

As TOTP relies on having pretty accurate system time (generally within ~30
seconds, with wiggle room up to a minute), we may need to add ntpd to our
appliance image, and a way to configure a pool of NTP servers.

## Deployment notes

Account recovery is harder in Hybrid Cloud because you probably don't have an
admin to fall back to.  We'd probably want text code recovery for those
situations.

Supporting SMS as second factor would require integration with Twilio or some
way to send SMS, which is 1) a bit of a non-starter for Private Cloud and 2)
scope creep

# Tasks and Estimated time requirements

Estimated total time: ~172 known story points.
(Which, given past experience, means something like ~2 solid man-months.)

### Mandatory
* SP and SPDB changes:
  * Implement TOTP protocol in Java with test vectors - 4 hours
  * Modify SP schema (with migration) to include two-factor secret, recovery codes, two-factor enabled - 4 hours
  * Expose second factor through DB abstraction layer - 4 hours
  * Figure out desired semantics of recovery codes - 4 hours
  * Expire each recovery code after use - 4 hours
  * Change protocol for SP login to also potentially throw ExRequireSecondFactor if user has enabled two-factor auth - 16 hours
  * Rate-limit login attempts - 32 hours
* User flows in web for:
  * Enabling two-factor - 16 hours (needs QR code, text code, requests against SP)
  * Disabling two-factor - 8 hours
  * Downloading/viewing text recovery codes - 8 hours
  * Providing second factor to log in - 8 hours
  * Providing recovery code to log in - 8 hours
* Client changes:
  * Adjust user login dialogs in client/teamserver to work with second factor - 16 hours
* Sparta changes:
  * Expose presence/absence of second factor through user management API in sparta (and update docs) - 4 hours
  * Implement user management API call to disable second factor for a user (and write docs) - 4 hours
* Deployment changes:
  * Add ntpd to appliance - 8 hours
  * Add a way to configure a non-default NTP server address - 16 hours
  * Write support article about two-factor auth - 8 hours

Internal testing can take place once SP and SPDB changes are live, but note
that the plumbing is generally way less time-consuming than the UX, so to
minimize risk, we should design the UX first, before implementing the algorithm
and backend.

### Optional
* Implement SMS delivery of second factor.  Requires:
  * integration with a 3rd party for sending SMS
  * storage of user phone numbers for delivering SMS
  * a way for users to indicate that they prefer to receive codes via SMS or just use their authenticator app

I'm pretty sure we don't want to bother with this in the first pass, and it
should be not terribly invasive to implement in a second pass.
