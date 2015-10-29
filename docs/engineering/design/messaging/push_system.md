# Push: Who, How, and Why

First, a system diagram:

     Unified Push Server
       ( push.arrowfs.org )               public cloud push networks
    +-----------------------+                 +----------+
    | +-------------------+ |                 |          |
    | |                   +--------+----------> APNS     +------>  iOS devices
    | | aeropush/unified  | |      |          |          |
    | |                   | |      |          +----------+
    | +---^---------^-----+ |      |
    |     |         |       |      |          +----------+
    | +---+---------+-----+ |      |          |          |
    | |                   | |      +----------> GCM      +------>  Android devices
    | | aeropush/nginx    | |                 |          |
    | |                   | |                 +----------+
    | +---------^---------+ |
    |           |           |
    +-----------|^----------+
                ||
           +---------------+
           |               |
           | private cloud |-+
     +-----> appliance     | |
     |     |               | |-+
     |     +---------------+ | |
     +------>|               | |
     ||      +---------------+ |
     ||         |              |
     ||         +--------------+
     ||
     ||
     ++[ messaging clients ]


## In Brief

A user with an iOS device signs in to AeroIM. The app asks for permission to
use Notifications; if granted, we get a unique identifier for that device, and
register it with the AeroIM Unified Push Server.

A message is delivered to an offline user when they are offline. The messaging
system notices the destination party is offline, and dispatches an alert to the
Unified Push Server. This is queued and delivered to the public cloud push
networks for all devices belonging to the user.


## Motivation

Why do we maintain a public push server?

Let's walk through notification to iOS clients as an example.

Our application has a particular private key and certificate associated with it
in the Apple Developer ecosystem. Proof of ownership of that certificate is
required to send push notification to devices that have registered the app (and
allowed it to use Notifications).

If an attacker were to get access to the certificate, they could send
notifications to any customer, without our knowledge. Therefore the certificate
information cannot be distributed directly to private-cloud appliances.

There is no way to send async notification to iOS devices other than APNS.

The public push server is a translation point between customer-visible
credentials and AeroFS-private credentials.


## Components

### aeropush/loader

The push server (at push.arrowfs.org for the alpha) uses the Ship-Enterprise
machinery. The loader on this machine ties together:

 - data
 - mysql
 - unified push
 - nginx

There is only one instance of this server; no need to deploy per customer.

TODO: this should be managed by aerofs-infra.


### aeropush/unified

The Unified Push Server is an open-source Java application from
[AeroGear](https://aerogear.org/push/).

This container is not publicly accessible - the only openings should be the
routes explicitly proxied by `aeropush/nginx`.

The service is responsible for:

 - registering and managing devices;
 - delivering notifications to devices on various public push networks
   (Android, iOS, and Windows)
 - managing delivery failure from push networks and updating device lists.

This container holds the AeroFS certificates needed to send alerts via the APNS
and GCM to our applications.

#### Unified Push Credentials

We manage a total of three identifier/secret pairs for this service. The actual
values are encoded in the `unifiedpush` database.

Unified Push is configured with one application - AeroIM - and two 'Variants' -
APNS and GCM.

Sending a notification to any registered device requires the "master id" and
"master secret" for the application. Each Variant has its own application
id/secret that is required to register devices of that type. We do not expose
any of these credentials to our end-users, even inside the private cloud
appliance.

We can update these credentials at any time, as long as we update the Unified
Push containers together.


### aeropush/nginx

This container is simply a reverse proxy for `aeropush/unified`.

It performs three main functions:

 - SSL termination (an efficient https frontend for http backend services);
 - validate requests given a customer username/password pair;
 - substitute a private application id/secret pair for requests to `aeropush/unified`.

The application id / secret pairs are encoded directly in the nginx
configuration for each exposed endpoint. Aside from Authentication
substitution, the nginx layer does not interfere with the requests being passed
through to Unified Push.

The following routes are exposed:

 - `/registry/android` maps to `/ag-push/rest/registry/device` with the
   appropriate Variant id
 - `/registry/ios` maps to `/ag-push/rest/registry/device` with the appropriate
   Variant id
 - `/ag-push/rest/sender` maps directly to `/ag-push/rest/sender`

See [AeroGear docs](https://aerogear.org/docs/specs/aerogear-unifiedpush-rest-1.0.x/overview-index.html)
for detailed specifics on the supported REST requests.

#### Credentials

We are using the simplest-possible revocable authentication scheme.

At the time we build the `aeropush/nginx` container, we generate an htpasswd
file (Apache-style) which can be handled directly by nginx. User ID/Customer
pairs can be given to alpha customers directly. If a customer credential is
leaked, or found to be a source of abuse, we simply remove it from the master
list and regenerate the container.

See `nginx/gen_password.sh` in the `aerogear-unified-push` project.

TODO: Automation and management of this credential list. Use a hash of the
customer license? Generate credential pair _into_ the license file? (this works
but needs coordination)

NOTE: key-value pairs in the license are automatically appended to the
properties in the configuration service. So creating credentials and baking
them into the license file is a simple, private distribution mechanism for
customers. The missing piece is communicating these values back to the push
server; or exposing a password-check function that looks at the generated
credentials. For now, hand-managed creds.

# Enabling Push on an appliance

Configuring an appliance to use the public push service is simple; append the
following to the external.properties:

    messaging_push_enabled=true
    messaging_push_address=https://push.arrowfs.org
    messaging_push_auth_value=Basic RUM5NjgyQkEtRkMyOC00MjU5LTk1RDYtQzUyRkI1NjEyQ0JFOkMyNTJEMTc0LTAwOUUtNDFFQS05N0ZGLUQ0NzQxODYwN0JCOA==


TODO: front-end (bunker) for these values.

# Current usage

If the device family is attached and is either APNS or GCM, the Trifrost
service will register the given device token with push services.
Note that trifrost is updated to expect the `messaging.push` configuration blocks.

Historically, XMPP would dispatch alerts - this is implemented in the Offline
plugin. In the future, presumably Sloth will be responsible for this.
Dispatching alerts to Unified Push is trivial now that the JID can be used
directly as the target alias.

However, trifrost currently has an internal endpoint that will forward requests
to the configured Push server. See `/notify` for information.

# EXAMPLE USAGE

    # Register an iOS device (note, alias is the JID/userId):
    curl -XPOST \
        -u 'EC9682BA-FC28-4259-95D6-C52FB5612CBE:C252D174-009E-41EA-97FF-D47418607BB8' \
        -H 'Accept: application/json' -H 'Content-type: application/json' \
        -d '{"deviceToken":"8691a380251592ca75f300a028397a76865b856","alias":"0d67a5683e484594a7d0fd47878ac839"}' \
        https://push.arrowfs.org/registry/ios

    # Send notification to all devices belonging to the given userId
    curl -XPOST \
        -u 'EC9682BA-FC28-4259-95D6-C52FB5612CBE:C252D174-009E-41EA-97FF-D47418607BB8' \
        -H 'Accept: application/json' -H 'Content-type: application/json' \
        -d '{"message":{"alert":"Hello hello!"},"alias":["0d67a5683e484594a7d0fd47878ac839"]}' \
        https://push.arrowfs.org/ag-push/rest/sender

