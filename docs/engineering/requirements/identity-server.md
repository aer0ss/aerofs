# Identity server

    v0.1 2015/03/23 jP initial draft requirements
    v0.2 2015/03/25 jP Add token-request options; make more OAuth-compatible;
        clarify trusted services.

N.B. the in-progress server code was removed in gerrit change I3c5822553dabc10a35f1ef7bba0a27f87f5520a9

A new microservice that provides a documented, public interface to our backend
identity systems.

The first function of this service is to provide opaque auth tokens to users
that can prove ownership of a valid email address.


## Motivation and history

Historically we have had a shared responsibility (and confusing
interdependencies) between the Web, SP, Sparta, and Bifrost components for
identity management. The addition of messaging features will introduce yet
another system.

Rather than continuing to build a web of interconnections, this document
proposes a new system that exposes a documented, public interface for
authentication. Over time, we can start to move implementation from the legacy
systems (SP specifically) until it is small enough to decommission.

Obviously this is a followthrough risk. The alternatives are all at least as
risky, architecturally.


## General Requirements

Identity has both public-facing and internal-only RESTful interfaces. The
public-facing API is documented.

### Public Interface: Create New Tokens

Users can request a new auth token once they have successfully authenticated.
Currently one authentication method is required - proof of email ownership.

Creating a new auth token may implicitly provision a user account.

#### Workflow

User requests a new token for a given email address. If the request is valid
(see section on Validating Requests), Identity sends an email to the address
with an included code.

The user signs in by providing the code; the client uses a validation endpoint
that requires the email address and matching code. If successful, a userid/auth
token pair is generated and returned.

The auth-token request must specify:

 - requested scope;
 - requested auth token lifetime;
 - whether a refresh token is required.

#### Auth Request step

The initial request for an emailed validation code must perform the following
checks:

 - if the email given is associated with an account, validation succeeds; no
   further checks are made.

 - if the appliance is configured to use an external authenticator (e.g.,
   ActiveDirectory/LDAP), search the external system for the given address. If
   it is not found, the request fails.

 - if the appliance is configured to use onboard authenticator (i.e.,
   Aero-managed passwords) the domain part of the address is checked against an
   optional whitelist. If the domain is not in the whitelist, the request fails
   with an explanatory message.

 - the service attempts to provision a user. This will occupy a paid seat; if
   the organization is using all the paid seats, validation will fail.


#### Verify step

An auth token will be returned when the user has proven ownership of the email.
This simply involves checking the provided code against those generated for
the account.

Each code must be usable at most once, and must auto-expire after a fixed
(configurable but short) interval.


#### Returned tokens

The following components are returned for a valid authorization request:

 - user id
 - device id
 - auth token
 - auth token expiry
 - refresh token

Auth tokens are associated with opaque user ids, not email addresses. This
design allows a future in which one user has multiple email addresses (in order
to support changing an address). The user id may be used to create
authorization headers.

Auth tokens are valid for a limited time. The default is 1 year, though this
could easily be much more aggressive.

The refresh token is used to request and replace a new auth token. Refresh
tokens do not expire, though they can be manually revoked.

The device id is required whe requesting a new auth token. Generating a new
auth token invalidates any previous auth tokens for that device.

NOTE: aeroim uses `base64(<userid> + ":" <auth_token>)` as the authorization
header content. It probably should use the device id instead...


### Public Interface: Refresh auth token

This endpoint requires a { `device_id`, `refresh_token` } pair. The server must
validate that the token in question was issued by this server, and has not been
revoked.

If so, any previous auth_tokens for the given device must be revoked
immediately.  A new auth token is created and returned.

The refresh token for this device is unchanged.


### Public Interface: Invalidate refresh token

This endpoint requires an auth_token authorization. The endpoint revokes the
refresh token created for any device that belongs to the given user.


### Limited-access Interfaces: validate token

The Identity server provides an endpoint that trusted internal services can use
to validate userid/token pairs. This is not exposed publicly to reduce risk.
Throttling is assumed to be implemented by the outer (public-facing) services.

If granular permissions are implemented, validation of a particular scope will
be supported here as well.


## Design: Dependencies

This component will require access to spdb - among other uses, this is the
single place we count occupied seats.

This component should have no direct dependency on SP. In the fullness of time,
we assume SP will reduce in scope and significance.


### Trusted services

The validation endpoint in this service can only be used by trusted services.
'Trusted' in this context means that an end-user assertion presented by the
service will be taken as a fact.

The identity service may accept a delegated token (a credential from an end
user that is being passed through) from a trusted service. It may also avoid
certain rate-limiting/throttling when taking requests from a trusted service.

Trusted services are those that correctly supply a per-deployment secret.

Per-service privileges (X and Y can verify tokens, Z can issue tokens) is not
currently in scope.


## Configuration

The following are service-specific configuration:

 - Service and Admin configuration blocks (per baseline)

The following should be configurable but are not currently exposed for
end-users:

 - auth token default lifetime


The following are installation-specific, and are obtained from existing
configuration service:

 - Database configuration block (jdbc connectivity)
 - Mail configuration block: smtp configuration.
 - Authenticator type and external service configuration.
