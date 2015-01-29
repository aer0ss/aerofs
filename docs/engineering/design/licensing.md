License design, enforcement, and structure
==========================================

This document is intended for AeroFS-internal consumption to explain how the
licensing system is designed and how it is expected to operate.

The key words "MUST", "MUST NOT", "REQUIRED", "SHOULD", "SHOULD NOT",
"SHALL", "RECOMMENDED", "MAY", and "OPTIONAL" in this document (in
uppercase, as shown) are to be interpreted as described in
[RFC 2119](http://www.ietf.org/rfc/rfc2119.txt).

This design is not expected to be 100% watertight.  A sufficiently-dedicated
attacker will be able to circumvent any countermeasures we create.  Thus, this
design aims to raise the bar to a certain level, with the understanding that
doing so will be a violation of the software agreement.

# Requirements

We want our software to enforce:

1. License validity dates
2. User limits
3. (OPTIONAL) feature limits

To do that, we need a token which our private cloud appliance can trust which
indicates:

  * the type of license
  * the window of validity for the license
  * the number of users authorized for this instance
  * the customer who paid for this (allows traitor-tracing in the case of piracy)

Revoking licenses is beyond the scope of this design, and can probably only be
resolved by legal action anyhow.

# The license file

Customers SHALL be given an opaque license file (aerofs-private-cloud.license).
This license file shall consist of a PGP-signed tar archive containing at
least:

  * A file named `license-info` in a UTF-8-encoded, linefeed-separated,
    key=value format, containing at least the keys `license_type`,
    `license_issue_date`, and `customer_id`.  `customer_id` refers to a unique
    integer associated with each Private Cloud customer.  `license_issue_date`
    refers to the UTC time at which the license file was generated in ISO8601
    datetime format.  Specifically, this field will be formatted as strftime(3)
    would format `%Y-%m-%dT%H:%M:%SZ`.  Depending on the value of
    `license_type`, other values may be expected:
    * if `license_type` is `standard`, then we also expect keys `license_valid_until`
      and `license_seats`.
      - The value of `license_valid_until` MUST be an ISO8601 datestamp, in
        YYYY-MM-DD format.  The license expires at the beginning of that day.
      - The value of `license_seats` MUST be a base-10 integer between 1 and
        2147483647.
    * currently, no additional license types are supported, but this mechanism
      allows for other types of license, like trial, unlimited, or clustered
      deployments.

The license file SHALL be encrypted and signed with a PGP key for
`licensing@aerofs.com`.  This key SHALL be stored offline and air-gapped from
the rest of company systems.  This key MUST NOT be placed on unencrypted
storage.  This key SHALL be backed up to a secure physical location, such as a
bank safe deposit box.

# Private Cloud website

(note: I'm using privatecloud.aerofs.com here, but we can change that if desired)

Users will download their license file from the AeroFS private cloud website.
This site will be a newly designed site that will deal only with private cloud
customers.  It will have its own organization and user database, separate from
the one use for our public cloud offering.

For each organization, it should have:
  * a nonempty list of site administrators, used only for `privatecloud.aerofs.com`
    * each admin should have an email/password
  * Address?
  * whether the organization has accepted our License Agreement
  * whether the organization is verified?
  * (OPTIONAL) whether the organization has requested a trial yet
  * (nullable) payment information
  * (nullable) latest license file
  * current license info (read from license file)

Once the customer has provided the requisite information and agreed to the
license, we will manually generate a license file for them and upload it to
privatecloud.aerofs.com.  The customer can then download the VM image and their
license file.

# Workflows

## Initial provisioning of license file

1. User registers an account on privatecloud.aerofs.com, creating a new organization.
2. User can invite additional email addresses to join the organization.
3. User must accept terms of service before accessing other site features.
4. User can request a trial or pay for a full license.
5. AeroFS team will receive requests, verify contact information, and generate and upload a license file
6. Once license file is available, user can download .ova and license file

## First VM configuration

1. User loads .ova file into virtualization software of their choice
2. User configures networking if needed; otherwise, visits site configuration page in browser
3. Site configuration page prompts for license file
4. User uploads license file that was downloaded from privatecloud.aerofs.com
5. Site configuration now allows site configuration
6. User completes site configuration as before
7. Product is ready to go

## License nearing expiry

We should email each of the admins known to privatecloud.aerofs.com, just as we would in the case of a software update.

OPTIONALly, we may want to display a warning on web pages viewed by admins.

## License renewal (previous license has not yet expired)

1. Admin visits site configuration page.  Site configuration page has option for uploading new license file.
2. Admin uploads new license file.  All new licensing rules are applied.  Users see no difference.

## License renewal (previous license has expired)

1. AeroFS does not sync (data is left in place, though), and user functionality on the website is disabled.
2. Admin visits any web page, sees "license expired" page and click on link to site configuration page.
3. Site configuration page prompts for new license file.
4. Admin uploads license file.
5. Site applies new license information, and restores functionality
6. AeroFS installations resume syncing.

# Enforcement

Admin will upload the license file to the setup service.  The setup service
will verify that the license file:

  * is signed by the correct PGP key
  * contains a license-info file
    * and the license-info file is valid, based on the license type.
      * For standard licenses, this means checking that the
        `license_valid_until` date is still in the future.

The setup service will issue a session cookie that indicates that the browser
that uploaded the license file is authorized to make configuration changes.

The setup service MUST, upon receiving a new license file, add the key-value
pairs relevant to license enforcement to standard config service responses for
servers.  For a stanard license, this includes at a minimum `license_type`,
`license_valid_until`, and `license_seats`. Services with more
granular licensing will use the values exposed through the configuration
service to configure their restrictions (for example, SP limiting active user
count, or (future work) enabling particular features)

To enforce the time limits placed on the license, the setup service SHOULD
schedule a periodic task to check the license validity (say, every hour or
day).  If the license is no longer valid, then the setup service SHOULD stop
all AeroFS services that are not essential to providing a new license to the
appliance.  (That is, it SHOULD stop tomcat, ejabberd, zephyr, havre, verkehr,
and ideally, also make all web pages save the setup-related ones 302 redirect
to a "license expired" page.  This can be done easily and robustly with nginx.)

Services MAY perform a check on service startup to see if the license is valid,
and terminate if the license is expired, as a fast path to service termination.

Additionally, SP MUST check the number of active users and the number of users
allowed by the license when attempting to provision new users to ensure that
all users are provisioned within the license's limits.

# Implementation notes

Since licensing is very core to the product and must be available both very
early on (before most things are configured) and even in the face of
self-imposed service failure (enforcing license expiry), it SHOULD have
absolutely minimal dependencies.  Ideally, the service will require only a
frontend service like nginx and otherwise be entirely self-contained.  This
ensures that all assets are available even if other services are stopped or
other packages are being upgraded.

Providing the appliance a license and site configuration are closely related
and are both tasks taken on by the exact same set of users - the site
administrators.  It thus makes sense to let the license file authenticate
the site administrator for the site configuration service.
The site configuration service MUST allow the license file to authenticate users.
The site configuration service MAY allow SP cookies to authenticate users.

The setup service and configuration service SHOULD be provided by the same
minimal service.  Site configuration is managed by admins, admins are
authenticated by license file, and licensing needs to modify (though perhaps in
a mimimalistic way) site configuration.

The current implementation provides the following routes in the config server:

  * `POST /set_license_file`
    POST content should include license_file=<urlsafe-base64 of the license file>
    as application/x-www-form-urlencoded.

    Returns:
    - `200 OK` if the license file was accepted.  The license file has been
      made the currently-active license file and is saved at
      `/etc/aerofs/license.gpg`.  Subsequent requests for config data will
      include any key-value pairs from the license file's `license-info` file.
      The caller may save the sha1sum of the data (before base64-encoding) as
      an authentication token for future requests.
    - `400 Bad Request` if the request was invalid, with response body as
      text/plain explaining the cause of the failure.  The cause could be any
      of the following:
      - `license_file` parameter was missing from the POST body
      - `license_file` parameter did not contain valid base64 data
      - `license_file` parameter contained base64 data, but the data contained
        did not bear a GPG signature from the AeroFS trusted root
      - `license_file` contained a valid license file, but that license was
        assigned to a different `customer_id` than the currently-active
        license.
      - `license_file` contained a valid, but expired license file

  * `POST /check_license_sha1?license_sha1=<hex-encoded sha1sum of license file>`
    Returns:
    - `200 OK` if the sha1sum matches that of the currently-active license file.
    - `400 Bad Request` if no `license_sha1` was provided, if `license_sha1`
      was not a 40-character hex digest, or if the sha1sum does not match that
      of the currently-active license file.
    - `500 Internal Server Error` if the instance this request is made against
      has no currently-active license file.
