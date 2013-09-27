Configuration Properties
========================

**Note**: Property values shown in the following text are default values. When no
default values are available, `<mandatory>` is used.

# common.properties

The file common.properties contains properties used by both AeroFS clients and servers.

    internal_email_pattern=

This property specifies a regular expression string. User IDs that match this pattern 
are treated as "internal" addresses. The concepts of internal and
external email addresses are used by read-only external shared folder rules, identity
management, and potentially other subsystems. The expression syntax follows
[Java standards](http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html).
Note that if you want literal `.`, use `\\.`

Example: `.*@google\\.com|.*@google-corp\\.net`


## OpenID properties

(Please See Appendix for complete examples.)

    openid.service.enabled=false

Whether to enable OpenId authentication (if enabled, this replaces credential auth).

    openid.service.session.interval=1

Polling frequency of the client waiting for OpenId authorization to complete, in seconds.

    openid.service.url=<mandatory>

URL of the Identity service. Note: This is the URL of the actual IdentityServlet.

Example: `https://transient.syncfs.com/openid`




# server.properties

The file server.properties contains properties used only by AeroFS servers. There may 
be sensitive information stored in this file such as service credentials,
AeroFS clients should not be able to access this file.

    shared_folder_rules.readonly_external_folders=false

Whether to enable read-only external folder rules. The system uses `internal_email_pattern` 
to determine internal vs external users. Therefore, the rules are enabled only if
`internal_email_pattern` is non-empty _and_ this property is true.

These rules are specific to certain enterprise deployments and are not normally enabled.
See class-level comments in ReadOnlyExternalFolderRules for more explanation on the rules.


## OpenID properties

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

Example: `https://*.syncfs.com`

    openid.idp.discovery.enabled=false

Whether to enable OpenID discovery. It may be disabled if YADIS discovery is not supported.

    openid.idp.discovery.url=

Discovery URL for the OpenId provider. Only used if discovery is enabled.

Example: `https://www.google.com/accounts/o8/id`

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

# Appendix: OpenID properties examples

## Google

    openid.service.enabled=true
    openid.service.timeout=300
    openid.service.session.timeout=10
    openid.service.session.interval=1
    openid.service.url=https://transient.syncfs.com/openid
    openid.service.realm=https://*.syncfs.com
    openid.idp.discovery.enabled=false
    openid.idp.discovery.url=
    openid.idp.endpoint.url=https://www.google.com/accounts/o8/ud
    openid.idp.user.uid.attribute=openid.identity
    openid.idp.user.uid.pattern=
    openid.idp.user.extension=ax
    openid.idp.user.email=openid.ext1.value.email
    openid.idp.user.name.first=openid.ext1.value.firstname
    openid.idp.user.name.last=openid.ext1.value.lastname

## Private deployment

This example parses an OpenId identifier of the form:
     https://exauth.example.com/user/jpile9/
Into:
     email: jpile9@example.com
     first: J
     last: Pile

(In this case, the example auth service doesn't support sreg _or_ ax.)

    openid.service.enabled=true
    openid.service.timeout=300
    openid.service.session.timeout=10
    openid.service.session.interval=1
    openid.service.url=https://sync.example.com/openid
    openid.service.realm=https://*.example.com
    openid.idp.discovery.enabled=false
    openid.idp.discovery.url=
    openid.idp.endpoint.url=https://exauth.example.com/auth
    openid.idp.endpoint.stateful=false
    openid.idp.user.uid.attribute=openid.identity
    openid.idp.user.uid.pattern=^https://exauth.example.com/user/((\\w)([a-zA-Z-_\\.\\+]*)\\d*)/$
    openid.idp.user.extension=
    openid.idp.user.email=uid[1]@example.com
    openid.idp.user.name.first=uid[2]
    openid.idp.user.name.last=uid[3]

