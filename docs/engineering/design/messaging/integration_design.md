# AeroIM for Private Cloud

In the last month, we converted the public AeroIM deployment system to be
redeployable on top of Docker with Ship-Enterprise. im.arrowfs.org is currently
deployed as a test balloon using this system.

This document describes the final integration points as well pull the messaging
features ever-more-tightly into AeroFS private cloud infrastructure.

Goal: Fast as possible "ready for friendly customer" without sacrificing
privacy for such a customer. We don't have to fill in every blank, but let's
not paint ourselves into a corner either.

A WBD implied by this is at the bottom.


## Changes to AeroFS-managed services (public infra)

### Private cloud directory service

An Organization represents a single billable entity. An Organization has at
least one email domain, and potentially many (company.com, company.co.uk,
etc.).

The Organization has at least one Administrator with an account on the private
cloud management system.

Lizard (the server behind privatecloud.aerofs.com) already supports all of the
above, including managing verified email accounts/credentials for
administrators.

Additions:

 - add to each organization a set of zero, one, or many email domains they
   control.
 - add to each organization the ability to set and retrieve a single appliance
   address. (Optional enhancement: add a checksum of the server certificate for
   self-signed certificates)
 - add a public REST endpoint that accepts an email domain and returns the appliance address, if one exists.

Initially, we can manually configure these settings for test accounts and
friendly organizations. Imagine some simple scripts that direct updates to
lizard's backing store.

Adding these elements to the self-service privatecloud interface will be a
simple change when we are ready to announce these features, and have the
policies in place for domain validation/verification.


#### privatecloud.aerofs.com : new "appliances" endpoint for directory services

    GET privatecloud.aerofs.com/appliances/{maildomain}

Return 404 to indicate no such email domain, or no appliance configured for
that organization.

Return 200 for a registered and configured appliance:

    {
        "host": string,
        "cert_hash": string (nullable, omitted if empty string)
    }

    host: network address of the AeroFS/AeroIM appliance (dotted-quad or
    hostname). Note, this can be a private/air-gapped/vpn'ed/nat'ed address; as
    long as _clients_ can reach it. We don't need to talk to this appliance from
    the internet at large.

    cert_hash: if enclosed, a hash of the certificate value known to belong to
    the server. The mobile AeroFS signin uses a similar mechanism to avoid
    leaking information in the face of DNS spoofing.

#### privatecloud.aerofs.com : behind-the-scenes changes

Small changes to a small service:

 - new table associating email domain with organization id.
 - new table recording appliance hostname for each organization.
 - mechanisms to update above for test orgs.


### Unified Push Server

The instance of AeroGear we run already can be used for ... hundreds?
thousands? of mobile clients.

Private cloud appliances will need to provide a simple credential of some kind
for unified-push requests. The Aerogear server already provides a secure token,
but it is not easily revocable once leaked.

TODO: (Authentication design TBD here. We can use something like a hash of the
license value as an authentication token, parsed and validated by an nginx
frontend. nginx can swap the authentication header for the one that AeroGear
expects)

Instances of the push gateway can be cloned and deployed on-premise for
customers that prefer a fully-onsite option. A customer can use a local gateway
by simply changing the locally-configured push gateway uri.

Note that the push services don't really work without leaking some information
to GCM/APNS, so there is a ceiling to the privacy protection we can offer in
general. MDM is assumed to be the better way forward for enterprise-class
customers. Suggest we spend as little effort as possible on this, beyond
providing an opt-out and managing carefully what we push through _any_ push
services.


## Identity management changes

### User experience

User signin experience is the same as it is in the current mobile client:

 - enter an email address, click Next;
 - enter a one-time pass code;
 - enter profile information if needed.

...i.e., magic, with hidden complexity. (Note the web client today is a little wonky
in this regard, it asks for name and email together)

### Actual sign-in sequence

    title Private Cloud Sign-In

    User->Client: Sign in "jon@example.com"
    Client->Lizard: GET /appliances/example.com
    Lizard->Client: { "host":"share.example.com"}

    Client->share.example.com: POST /users/jon@example.com
    share.example.com->SMTP: "Your code is 123456"
    SMTP->User: "Your code is 123456, ☃"
    User->Client: 123456
    Client->share.example.com: POST /users/jon@example.com?code=123456
    share.example.com->Client: "200 OK"


![Sequence diagram for above lives at: docs/engineering/design/messaging/privatecloudsignin.png](http://www.websequencediagrams.com/cgi-bin/cdraw?lz=dGl0bGUgUHJpdmF0ZSBDbG91ZCBTaWduLUluCgpVc2VyLT5DbGllbnQ6ABIFIGluICJqb25AZXhhbXBsZS5jb20iCgAcBi0-TGl6YXJkOiBHRVQgL2FwcGxpYW5jZXMvACILCgAeBgBMCnsgImhvc3QiOiJzaGFyZS4ATQx9CgBSCQAMETogUE9TVCAvdXNlcnMvAIEEDwoAOxEtPlNNVFA6ICJZb3VyIGNvZGUgaXMgMTIzNDU2IgpTTVRQLT5Vc2VyAAwWLCDimIMiAIIBDwA3BgBrNz9jb2RlPQA8BwCBGxMAgm0IIjIwMCBPSyIKCg&s=vs2010)


### LDAP identity stores

AeroIM does not currently respect the "account must exist in backing store"
rule. For systems that use an external authenticator, the signin mechanism must
validate the user's email is part of the allowed set.


### Email domain whitelisting

AeroFS does not currently "know" email address patterns that are permitted
(other than the restrictive "must belong to LDAP store" rule). Without this
check, a user can register an arbitrary mail address on a private-cloud system.


## Appliance updates

The AeroIM server infrastructure is already deployed as a collection of Docker
containers, tied together with crane through the magic of Ship-Enterprise(tm).

### New loader image

We can achieve what we need with the existing ship-enterprise tools, building
the new combined artifact under the name "aero-loader".

Much the same as the existing aerofs appliance with some new services added:

     - blurber
     - xmpp
     - profile (strip down, rename to ____)

Updated intra-container dependencies:
     - xmpp on identity (as spdb conduit)
     - updated nginx to resolve xmpp, blurber, profile.

### Updates to nginx frontend

A unified nginx will need to serve some new backend sites.

     - Add all backend-sites from the AeroIM nginx server. Put these under /messages/ ? Needs a top-level directory
     - Add the backend site for the static AeroIM web resources (index.html, bundle.js, css)

### Configuration service updates

The AeroIM processes will expect database configuration, service-to-service
auth information, and name-related configuration from the config service.

New configuration options will be added to the server configuration:

 - messaging enabled (boolean, default false). An umbrella for all messaging services.
 - permitted mail domains (array of regex strings, default ['*']). Used in open signup.
 - push notification enable (boolean, default true)
 - push notification address (uri, default push.aerofs.com:8080/ag-push)
 - push notification authorization value (string, everything after "Authorization: " expected by the server.)


For initial tests;

    user.mail.domains = *
    messaging.service.enabled = true
    messaging.push.enabled = true
    messaging.push.address = https://im.arrowfs.org/push/
    messaging.push.authorization = base64(aerogear appid + : + aerogear master secret)


### AeroIM updates: xmpp

The integration with the Push notification service must be updated to read
config values from config.service.

### AeroIM updates: profile

Rip out some code that has migrated to xmpp infrastructure.

Use the configuration service to find / talk to the Push server, mysql, etc.

Talk to the identity service to maintain the license counts correctly.


# Tasks

    Unified Push
        Stand up central unified-push server
            (Todo: use ship-enterprise or skip docker and build a oneoff?)
        Design for appliance->unifiedpush auth
            revocable tokens
            known to appliances
            ideally zero-configuration
        Local nginx frontend
            SSL termination
            Authorization header replacement


    Appliance build
        Add new values to configuration templates

        New loader image

        Integrated nginx image
            Add top-level endpoint for messaging
            configure backends appropriately

        appliance integration testing


    AeroIM updates
        Profile server
            Remove dead functionality
            Call out to identity service for account creation/validation
            Get Push configuration from config service

        XMPP providers
            Get Push configuration from config service


    Directory Service
        Lizard
            Database updates
            Route updates
            Testing
            Deployment

        iOS Client
            Update to use directory service

        Android Client
            Update to use directory service

        Web Client
            (optional) build unified frontpage with redirect


    Customer-readiness
        LDAP integration
            Design (pull out of sp? duplicate or call across?)
            Implementation


    UI
        Identity configuration: permitted email domains.
        Enable/disable push notification
        Configure unified-push notification address
            Address
            Configured cert (if not using public-signing infra)

