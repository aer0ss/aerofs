Configuration Properties
========================

# Overview

The configuration system uses a flask templating mechanism. Properties are stored in the file /opt/config/properties/external.properties. Templates are stored in /opt/config/templates/*. The only file that should be edited by site engineers is the external.properties file. Similarly, the site configuration interface only edits the external.properties file. Templates are part of the confiuration package and will be overwritten on package update. Thus, never edit them.

                     -----------------------
                     | external.properties |
                     -----------------------
                               |
                               v
           -----------------------------------------
           |                   |                   |
           v                   v                   v
    --------------      --------------      --------------
    |client.tmplt|      |common.tmplt|      |server.tmplt|
    --------------      --------------      --------------

The key values stored in the external.properties file are substituted into the templates by flask. Key names in external.properties are not visible to configuration clients.

Example:

external.properties:

    base_host=aerofs.com

client.tmplt

    updater.server.url=https://{{ base_host }}:8080/

common.tmplt

    web.server.url=https://{{ base_host }}/

server.tmplt

    ca.server.url=http://{{ base_host }}:1029/

Then the client configuration interface will give you:

https://share.syncfs.com/config/client

    updater.server.url=https://aerofs.com:8080/
    web.server.url=https://aerofs.com/

And the server configuration interface will give you (note that the server interface is only accessable from the server itself):

https://share.syncfs.com/config/server

    updater.server.url=https://aerofs.com:8080/
    ca.server.url=http://aerofs.com:1029/

# external.properties

All externally configurable properties (described above).

    configuration_initialized=false

A boolean flag indicating whether or not the private deployment configuration system has been initialized or not. Used by the web module to redirect to the setup page when we have not configured the system yet.

    support_address=support.aerofs.com

The "From Email" in our email communications.

    base_host=

The private deployment system hostname.

    email_host=
    email_password=
    email_user=

SMTP credentials. When set to "localhost", "", "" respectively, the system used local mail relay.

# client.tmplt

Generally propreties in this category are related to the updater, or are environment variable specific.

Example: This disables the defect sending dialogue on the client:

    gui.tray.enable_defect_dialogue=false

# common.tmplt

The file common.properties contains properties used by both AeroFS clients and servers.

    internal_email_pattern=

This property specifies a regular expression string. User IDs that match this pattern  are treated as "internal" addresses. The concepts of internal and external email addresses are used by read-only external shared folder rules, identity management (see [here](requirements/id_management.html)), and potentially other subsystems.

The expression syntax follows [Java standards](http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html). Note that if you want literal `.`, use `\\.`

If the string is emtpy, all the users are treated as internal users.

Example: `.*@google\\.com|.*@google-corp\\.net`

    url_sharing.enabled=true
   
Whether to enable URL sharing. When disabled, users can't create new links or access existing links. Existing links will regain access once this variable is re-enabled.

Note that AeroFS clients need to restart to hide the "Create Link" option from the shell extension.

    lib.anchor.default_location_windows=
    lib.anchor.default_location_osx=
    lib.anchor.default_location_linux=

The default location for the parent of the root anchor folder. This allows a site operator to override the default location for all installations (or only for certain operating systems).

Note that this describes the _parent_ of the AeroFS folder that will be synced.
Also note the character formatting needed for backslashes.

No default value; if missing, clients will use the compiled-in values for public deployment.

For these 3 properties, the client supports substitution from the user's environment.

- Given `${ENV_VAR}`, the client will attempt to substitute it with the value of `$ENV_VAR` on
Linux and Mac OS X and `%ENV_VAR%` on Windows.

- If the environment variable is not found, the text, `${ENV_VAR}`, will be left as is.

- Note that the substitution is _case-sensitive_, e.g. `${HOME}` is not equivalent to `${home}`.

- Even though `%LOCALAPPDATA%` isn't defined on Windows XP, the client supports it on Windows XP and
it will be set to `%USERPROFILE%\Local Settings\Application Data`.

- On Linux and Mac OS X, `~` will be expanded to the user's home directory. The way this is done
is by taking System property, `user.home`, provided by the JVM which should coincide with `$HOME`.

Example: `lib.anchor.default_location_windows=C:\\Storage\\` - the default location will be C:\Storage\AeroFS.

Example: `lib.anchor.default_location_osx=/storage_dir/` - the default location on OSX will be /storage_dir/AeroFS.

Example: `lib.anchor.default_location_windows=${LOCALAPPDATA}\\arrow` - the default location will
be %LOCALAPPDATA%\arrow\AeroFS on Windows Vista and up and it will be
%USERPROFILE%\Local Settings\Application Data\arrow\AeroFS on Windows XP.

Example: `lib.anchor.default_location_osx=~` - the default location will be $HOME/AeroFS.

### Identity properties

    lib.authenticator=local_credential

The authentication mechanism used by AeroFS to validate end-users. Possible options are:

- `local_credential` : The user will provide a username and credential that will be verified
locally (on the signin server). This requires the user to be created in advance through
the normal signup process.

- `external_credential` : The user will prove their identity using a username and credential
that will be passed through to an identity authority (LDAP). This implies the credential
should not be hashed on the client side.

- `openid` : The user will prove their identity out-of-band with a URI-based signin mechanism.
This means the client will use the SessionNonce/DelegateNonce mechanism and poll for the
asynchronous authentication completion. The client can expect some user-agent redirect
to the IdentityServlet. Client should poll on the session nonce for the out-of-band
authentication to complete.

&nbsp; <!--I don't know why, but this extra line is necessary to render the next line properly-->

    identity_service_identifier=OpenID or LDAP

A short, user-visible name for the external identity service.
This will be displayed to end-users in the context of "sign in with {}",
"a user without {} accounts", etc, where {} is replaced with the identifier.
The default value depends on the authenticator type.

Example: `SAuth`
Example: `OpenID`

### OpenID properties

OpenID configuration is used if the `lib.authenticator` is set to `openid`.
Otherwise these properties will be ignored.

(Please See Appendix for complete examples.)

    openid.service.session.interval=1

Polling frequency of the client waiting for OpenId authorization to complete, in seconds.

    openid.service.url=<mandatory>

URL of the Identity service. Note: This is the URL of the actual IdentityServlet.

Example: `https://transient.syncfs.com/openid`

# server.tmplt

The file server.properties contains properties used only by AeroFS servers. There may
be sensitive information stored in this file such as service credentials,
AeroFS clients should not be able to access this file.

    web.enable_appliance_setup_data_collection=false

Whether to track the progress of initial AeroFS Appliance setup with a trial license.

    last_smtp_verification_email=

Internal use only. This property remembers the last email address the user used to send SMTP
verification email.


    sharing_rules.restricted_external_sharing=false

Whether to restrict external sharing (Bloomberg-specific rule). The system uses `internal_email_pattern`
to determine internal vs external users. Therefore, the rules are enabled only if
`internal_email_pattern` is non-empty _and_ this property is true.

These rules are specific to certain enterprise deployments and are not normally enabled.
See class-level comments in ReadOnlyExternalFolderRules for more explanation on the rules.

    email.sender.public_host=<mandatory>

Hostname of the outbound email server. Special handling occurs if this is `localhost`.

Example: `email.sender.public_host=smtp.sendgrid.net`


    email.sender.public_port=25

Port number to use on the email server. Defaults to 25, like a gentleman.


    email.sender.public_username=

If provided, the username to authenticate with the SMTP server.

Default is blank, which indicates no authentication.

    email.sender.public_password=

If provided, the password to use to authenticate with the STMP server.

    email.sender.timeout=10000

Timeout value, in milliseconds, for a response from SMTP commands.

    email.sender.connection_timeout=60000

Timeout value, in milliseconds, for a connection to the outbound SMTP server.

    open_signup=false

Whether to allow self sign-ups. Otherwise, users must be invited by existing users
(either administrators or folder owners) to join the system.

    show_quota_options=false
    
Whether to display quota options on the Web user interface (under organization management).

### OpenID properties

(Please See Appendix for complete examples.)

    openid.service.timeout=300

Timeout for the entire OpenId flow, in seconds.
This timeout is used to expire the delegate nonce; once that happens, a client trying
to talk to the same nonce will get ExBadCredential. So this is effectively the maximum
time the client will wait for the web signin.

    openid.service.session.timeout=10

Timeout for the session nonce, in seconds. This is the timeout
only after the delegate nonce is authorized but before the session nonce
gets used. This only needs to be as long as the retry interval in the session
client, plus the max latency of the session query.

    openid.service.realm=<mandatory>

The security realm for which we are requesting authorization.
This is an OpenId concept, * is supported.

Example: `https://*.example.com`

    openid.idp.endpoint.url=

Endpoint URL used if discovery is not enabled for this OpenId Provider

Example: `https://www.google.com/accounts/o8/ud`

    openid.idp.endpoint.stateful=true

Enable or disable creation of an association with Diffie-Helman key exchange.
If set false, the dumb-mode verification is used instead.

    openid.idp.user.uid.attribute=openid.identity

Name of the HTTP parameter we should use as the user identifier in an auth response.

    openid.idp.user.uid.pattern=

An optional regex pattern for parsing the user identifier into capture groups. If this
is set, the capture groups will be available for use in the email/firstname/lastname
fields using the syntax uid[1], uid[2], uid[3], etc.

NOTE: If this is not set, we don't do any pattern-matching (do less is cheaper)

NOTE: capture groups are numbered starting at _1_.

Example: `^https://www.google.com/accounts/o8/id\?id=([\w\d]*)$`. This pattern puts everything after id= into uid[1].

    openid.idp.user.extension=

Name of the openid extension set to request, or can be empty. Supported extensions are:

- `ax` for attribute exchange;
- `sreg` for simple registration (an OpenId 1.0 extension)

Example: `ax` for Google and others...

    openid.idp.user.email=openid.ext1.value.email

Name of an OpenID parameter that contains the user's email address; or a pattern that
uses the uid[n] syntax. It is an error to request a uid capture group if
`openid.idp.user.uid.pattern` is not set.

Example: `openid.ext1.value.email` if `openid.idp.user.extension=ax`

Example: `uid[1]@syncfs.com` if `openid.idp.user.uid.pattern` puts the account name in `$1`

    openid.idp.user.name.first=openid.ext1.value.firstname

Name of an openid parameter that contains the user's first name; or a pattern that
uses the uid[n] syntax. It is an error to request a uid capture group if
`openid.idp.user.uid.pattern` is not set.

Example: `openid.ext1.value.firstname` for ax

Example: `openid.sreg.fullname` for sreg; fullname only

    openid.idp.user.name.last=openid.ext1.value.lastname

Name of an openid parameter that contains the user's last name; or a pattern that
uses the uid[n] syntax. It is an error to request a uid capture group if
`openid.idp.user.uid.pattern` is not set.

Example: `openid.ext1.value.lastname` for ax

Example: `openid.sreg.fullname` for sreg; fullname only

### LDAP authentication properties

LDAP configuration is used if the `lib.authenticator` is set to `external_credential`.
Otherwise these properties will be ignored.

(Please See Appendix for complete examples.)

    ldap.server.ca_certificate=

If the LDAP server does not have a publicly-signed certificate, the cert can
be supplied here. It will be added to the trust store only for LDAP server connections.

No default. Note the formatting newlines in the following example.

Example:    `ldap.server.ca_certificate=-----BEGIN CERTIFICATE-----\nMIIFKjCCBBKgAwIBAgID...`

    ldap.server.host=

Host name of the LDAP server. Required.

Example:    `ldap.server.host=ad.example.com`

    ldap.server.port=389

Port on which to connect to the LDAP server. Default is 389 for ldap protocol with StartTLS,
Set port to 636 for ldaps.

Example:    `ldap.server.port=389`

    ldap.server.security=tls

Configure the socket-level security type used by the LDAP server. The options are:

- `none`: use unencrypted socket (Not recommended, as this could expose user credentials
to network snoopers)

- `ssl`: use LDAP over SSL (the ldaps protocol).

- `tls`: use the LDAP StartTLS extension.

Example:    `ldap.server.security=ssl`

    ldap.server.maxconn=10

Maximum number of LDAP connection instances to keep in the pool.

    ldap.server.timeout.read=180

Timeout, in seconds, after which a server read operation will be cancelled.

    ldap.server.timeout.connect=60

Timeout, in seconds, after which a server connect attempt will be abandoned.

    ldap.server.principal=

Principal on the LDAP server to use for the initial user search.

Example:    `ldap.server.principal=CN=admin`

    ldap.server.credential=

Credential on the LDAP server for the search principal.

Example:    `ldap.server.credential=secret`

    ldap.server.schema.user.base=

Distinguished Name (dn) of the root of the tree within the LDAP server in which
user accounts are found. More specific DNs are preferred.

Example:    `ldap.server.schema.user.base=dc=users,dc=example,dc=com`

    ldap.server.schema.user.scope=subtree

The scope to search for user records. Valid values are "base", "one", or "subtree".
The default is "subtree".

- base : only the object specified by ldap.server.schema.user.base will be searched

- one : the immediate children of the ldap.server.schema.user.base object will be
searched, but not the base object itself.

- subtree : search the base object and the entire subtree of that object.

Example:    `ldap.server.schema.user.scope=subtree`

    ldap.server.schema.user.field.firstname=

The name of the field in the LDAP object that holds the first name.

Example:    `ldap.server.schema.user.field.firstname=givenName`

    ldap.server.schema.user.field.lastname=

The name of the field in the LDAP object that holds the last name.

Example:    `ldap.server.schema.user.field.lastname=sn`

    ldap.server.schema.user.field.email=

The name of the field in the LDAP object that holds the email address.
This will used in the user search.

Example:    `ldap.server.schema.user.field.email=mail`

    ldap.server.schema.user.field.rdn=

The name of the field that contains the relative distinguished name - that is,
the field that will be used in the bind attempt.

Example:    `ldap.server.schema.user.field.rdn=dn`

    ldap.server.schema.user.class=

The required object class of the user record. This will be used as part of
the user search.

Example:    `ldap.server.schema.user.class=inetOrgPerson`

The following example, the configuration fields for email and class are
combined into an LDAP search for matching user records:

Example:

    ldap.server.schema.user.field.email=mail
    ldap.server.schema.user.class=inetOrgPerson

    # given the email address "jon@example.com", the search will be
    # as follows. Note that LDAP uses postfix notation.
    #   (&(mail="jon@example.com")(objectClass=inetOrgPerson))


# Appendix: OpenID properties examples

### Google

    lib.authenticator=openid
    openid.service.timeout=300
    openid.service.session.timeout=10
    openid.service.session.interval=1
    openid.service.url=https://share.example.com/openid
    openid.service.realm=https://*.example.com
    openid.idp.endpoint.url=https://www.google.com/accounts/o8/ud
    openid.idp.user.uid.attribute=openid.identity
    openid.idp.user.uid.pattern=
    openid.idp.user.extension=ax
    openid.idp.user.email=openid.ext1.value.email
    openid.idp.user.name.first=openid.ext1.value.firstname
    openid.idp.user.name.last=openid.ext1.value.lastname

### Private deployment

This example parses an OpenId identifier of the form:

     https://openid.example.com/user/jpile9/

Into:

     email: jpile9@example.com
     first: J
     last: Pile

(In this case, the example auth service doesn't support sreg _or_ ax.)

    lib.authenticator=openid
    openid.service.timeout=300
    openid.service.session.timeout=10
    openid.service.session.interval=1
    openid.service.url=https://share.example.com/openid
    openid.service.realm=https://*.example.com
    openid.idp.endpoint.url=https://openid.example.com/auth
    openid.idp.endpoint.stateful=false
    openid.idp.user.uid.attribute=openid.identity
    openid.idp.user.uid.pattern=^https://openid.example.com/user/((\\w)([a-zA-Z-_\\.\\+]*)\\d*)/$
    openid.idp.user.extension=
    openid.idp.user.email=uid[1]@example.com
    openid.idp.user.name.first=uid[2]
    openid.idp.user.name.last=uid[3]

# Appendix: LDAP properties examples

### UNIX-style LDAP server

    lib.authenticator=external_credential
    ldap.server.ca_certificate=-----BEGIN CERTIFICATE-----\nMIIFKjCCBBKgAwIBAgID...075\n-----END CERTIFICATE-----
    ldap.server.host=ldap.example.com
    ldap.server.port=389
    ldap.server.security=ssl
    ldap.server.principal=CN=admin
    ldap.server.credential=secret
    ldap.server.schema.user.base=dc=users,dc=example,dc=com
    ldap.server.schema.user.scope=subtree
    ldap.server.schema.user.field.firstname=givenName
    ldap.server.schema.user.field.lastname=sn
    ldap.server.schema.user.field.email=mail
    ldap.server.schema.user.field.rdn=dn
    ldap.server.schema.user.class=inetOrgPerson

### ActiveDirectory server

    The following example searches a Windows domain called "borg.jonco.lan"

    lib.authenticator=external_credential
    ldap.server.ca_certificate=-----BEGIN CERTIFICATE-----\nMIIFKjCCBBKgAwIBAgID...075\n-----END CERTIFICATE-----
    ldap.server.host=ad.example.com
    ldap.server.port=636
    ldap.server.security=ssl
    ldap.server.principal=Administrator@borg.jonco.lan
    # Also valid:
    #    ldap.server.principal=DOMAIN\\Administrator
    #
    ldap.server.credential=secret
    ldap.server.schema.user.base=cn=Users,dc=borg,dc=jonco,dc=lan
    ldap.server.schema.user.scope=subtree
    ldap.server.schema.user.field.firstname=givenName
    ldap.server.schema.user.field.lastname=sn
    ldap.server.schema.user.field.email=mail
    ldap.server.schema.user.field.rdn=userPrincipalName
    ldap.server.schema.user.class=organizationalPerson
