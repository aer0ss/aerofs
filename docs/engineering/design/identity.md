Identity Services: design
=========================

This document describes the in-progress design for an identity system that
unifies aeroim and aerofs, as well as resolving some technical debt in the
existing systems.


## System Diagram

                        +-----------------+       +-----------+
                        |                 +-------> spdb      |     Accounts/Sessions/ACLs
                        | Identity        |       | (accounts)|     Layer
                        | Service         |       +-----------+
                        |                 |
                        +--^-----^-----^--+
                           |     |     |
                        +--+     |     +--------+
                        |        |              |
                        |        |              |
    +--------+ +--------+-+  +---+------+  +----+--+                Authentication
    | tokens <-+          |  |          |  |       |                Layer
    | (db)   | | Email    |  | Bifrost/ |  |  SP   |
    +--------+ | Verif.   |  | Sparta   |  |       |
               |          |  |          |  |       |
               +---^------+  +----^-----+  +---^---+
                   |              |            |
                   |              |            |
                   +              +            +
                AeroIM        Oauth/web      Web, desktop
                clients       clients        clients


This section describes the layers (and then the individual services) in the
above diagram. This only covers identity-related content.


### Account/Sessions/ACL Layer

Services at this layer maintain the list of valid accounts (as well as disabled
or deleted accounts). Simple verification ("does this account exist?", "Does
this account have administrative privilege?") also lives at this layer.

This layer does *not* store credentials (auth tokens, etc.) with one exception.
A user's hashed and salted password may be stored, modified, or verified by
this layer. This has more to do with legacy (building atop SP) than anything
else.


#### spdb

As much as possible, this database layout is maintained (to avoid a big-bang
change from sp).

This database currently records:

 - the set of users (valid and deactivated)
 - locally-managed credentials (if supported),
 - ACLs

This database is the single system of record that determines the current number
of licensed users.


#### Identity Service

Responsible for creating, updating, deleting, and verifying user accounts.

There are no public interfaces to this service.

Connections from immediate clients (services in the Authentication layer) must
be verified somehow (service-to-service auth). However, the connections do not
pass through end-user credentials.

For example: Bifrost authenticates user U. Then Bifrost must prove itself to
Identity using it's own stored credential; it does not rely on U's identity to
do so.


### Authentication Layer

Services at this level provide authentication (specifically, identity
verification) for client applications. Each service may support a different
mechanism for providing identity.

These services all expose a public interface of some kind.

Note that we do not currently provide a way to subscribe to account changes.
Accounts may be deactivated/deleted at any time; therefore services in the
Authentication layer must re-verify account validity with the Identity Service.
The verify call should be simple enough in both complexity and time; however,
services may implement some time-limited caching rather than verifying every
call.


#### Email Verification Service

Responsible for validating that a client has ownership of a particular email
address.

The legacy use of this service is as the credential-verification service for
AeroIM. We can also leverage this component to allow signin from the web for
traditional AeroFS. ("Forgot your password? Sign in with a one-time code we
will email to you...")


#### Sparta/Bifrost

Two services with slightly-overlapping responsibilities.

 - User administration. Leverage admin privilege to create a user, reset a
   user's password, change account details.
 - OAuth token management. Create limited-time tokens for a particular account
   (and possibly scoped to a particular resource). Validate OAuth bearer
tokens.

The management of the token (OAuth token and refresh token) is, and will be,
strictly performed by Sparta/Bifrost. Management of the account that _owns_ the
OAuth token is performed by the Identity service.

Creation of an OAuth token uses SP's "generate mobile access code" flow, which
is a confusing surprise for client applications (shelob and similar).


#### SP

Two letters with many responsibilities.

 - Account creation. Initial signup, including auto-provisioning of accounts
   backed by LDAP/OpenID, occurs here.
 - Organization management.
 - Verification of username/credential pairs
 - Session management. After credential-based signin, Tomcat does some kind of
   poorly-understood session magic.
 - Delivery of single-use authentication tokens (nonces) for Mobile device
   signin.


SP has direct interaction with the SP database, and incorporates semi-complicated
transaction logic for creating users.

We will be able to replace some of the direct "read/write to spdb" code with
http calls over to the Identity Service. This falls under the "technical debt"
category for now.

Moving the concept of a "signed-in session" outside of SP will simplify life
for some signins - not having to use the mobile-access-code nonce mechanism for
the creation of Bifrost tokens being a great example.

