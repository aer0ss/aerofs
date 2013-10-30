# Identity management

AeroFS Private Cloud provides three ways to manage user accounts: OpenID, LDAP/AD, and 
AeroFS managed accounts (i.e., scrypted credential is matched against aerofs_sp.sp_user).

### Internal vs. external users

We distinguish **internal** and **external** users by email address pattern matching
against the `internal_email_pattern` [configuration property](../conf_properties.html).

External users always log in with AeroFS managed accounts.

Internal users always log in with OpenID or LDAP if OpenID or LDAP is enabled.
Otherwise they always log in using AeroFS managed accounts.
      
There is no escape hatch for these user types, i.e. a user with an external 
address being treated as an internal user or vice-versa.

The password-reset flow needs to be defanged for internal users.

### Updating `internal_email_pattern`

When the email patten has changed, there is the possibility of migrating
users. This is supported as follows:

- moving users from external to internal:

  no server-side or DB change is required. We do not automatically nuke the stored
  credential, though we should; an attacker could talk directly to SP using the
  (defunct) password and bypass OpenID. Note that this does not affect LDAP users,
  since user pattern matching is done in SP in case of LDAP.

- moving users from internal to external:

  The users will need to be told to reset their password the first time they sign
  in. (They will not have an existing AeroFS password).

In both migration cases, there is a small gap that we could iron
out by performing credential fixups on SP. However this is somewhat
dependent on getting a change notification from the configuration
server. As it does not leave a huge hole, suggest we mark this
for future enhancement.
      
### Other notes
      
We use the internal/external decision to decide what UI elements to show the user;
no security decisions are made on the client side.
