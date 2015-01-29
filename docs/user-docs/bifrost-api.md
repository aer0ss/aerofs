# Bifrost, the API

> "Bifrost: Because Heimdal is inconsistently spelled, plus it was already taken"

[OAuth]: http://http://tools.ietf.org/html/rfc6749 "OAuth spec, RFC 6749"


# 0. tl;dr

The entire OAuth flow in five bullets:

1. Client initiates an authorization request. The initial response includes only a state variable that holds context for the life of the OAuth interactions.

2. User authenticates with OAuth. This may be by providing a username/credential pair, or by providing a one-time credential. The one-time credential is a nonce from SP; QR signin works this way.

3. When authentication completes, we provide an access_code (it is embedded in a URL query param and sent to the redirect URI).

4. Client exchanges the access_code for an access_token. The client should store the token somewhere safe. It can use the access_token as the HTTP auth mechanism.

5. A resource server, upon receipt of an access_token as an authentication token, can verify it with the OAuth server. Doing so returns a handful of user attributes.


# 1. Preconditions

The following setup needs to occur before any users will be able to sign in.

## Provision the resource server

The resource server is the application or server that will be verifying access_tokens.

The resource server will be configured with an id and credential that is required when verifying tokens. The AeroFS "Havre" resource server is automatically provisioned.


## Provision the client

The client is the application that will be authorizing users and providing the access_token to a Resource Server as evidence-of-authority.

The client will receive a client id and secret credential that it must use when talking to Bifrost.

Clients are registered and administered through the AeroFS web administration UI.


## TLS

Assume all communication described herein occurs on secured links. No basic HTTP is permitted.


# 2. Get A Token

A user can request an access token once they have established their identity. This section describes those interactions.

## Get Authorization

This is per [OAuth].

The Authorization Request starts the OAuth signin process.

### Authorization Request

Part   | Value
------:|------
Path   | `/authorize`
Method | `GET`
Query Params | As described below
Body   | None

----
Query parameters:

Field          | Req?  | Description
---------------:|------|------------
`response_type` | Req  | Must be `code`
`client_id`     | Req  | Identifier of a configured client (application id). This is received on client registration.
`redirect_uri`  | Req  | A URI to which authorization responses, including errors, will be redirected. This must match the redirect_uri entered during client registration.
`scope`         | Opt  | Must be a legal scope established for the resource server/client. Valid values in current release: `readonly`. This is optional if the client was registered with a default scope; otherwise this field is required.
`state`         | Opt  | A string that will be roundtripped and passed back to the redirect URI. Recommended: an unguessable string. This should be used to prevent cross-site request forgery attacks.

### Errors

HTTP Response | Sample cases
-------------:|-------------
`302`         | See below. Other errors may be returned via a 302 in some cases, but not always.
`400`         | Invalid or missing `client_id` or `redirect_uri`.

### 400 Error Response

If the `client_id` or `redirect_uri` are invalid, the `redirect_uri` cannot be trusted, and the error will be returned to the user with a 400 status code.

### 302 Error Callback

If the `redirect_uri` is valid and any other error is encountered, the user will be directed to the `redirect_uri` with the following query params. This is per [OAuth].

Param               | Req | Description
-------------------:|-----|------------
`error`             | Req | One of the error codes in [section 4.1.2.1 of the OAuth 2.0 spec](http://tools.ietf.org/html/rfc6749#section-4.1.2.1)
`error_description` | Opt | A human-readable description of the error
`state`             | Opt | If `state` was specified in the request, it will be present here. If the two values do not match, then the callback was initiated by a third-party site, and the process should be aborted.

### 302 Success Callback

If the user grants authorization, they will be redirected to the `redirect_uri` with the following query params. This is per [OAuth].

Param   | Req | Description
-------:|-----|------------
`code`  | Req | An authorization code that can be traded for an access token (see the next section)
`state` | Opt | If `state` was specified in the request, it will be present here. If the two values do not match, then the callback was initiated by a third-party site, and the process should be aborted.


## Get an Access Token

Once an `access_code` has been generated, the client may exchange the `access_code` for an `access_token`. If refresh tokens are enabled for this client, or if the access code has a defined expiry time, these will be reported in the response.

Two types of access code are supported:

- OAuth access codes, returned by the Authorize workflow described above

- Mobile access codes from the AeroFS website; these are an extension to the base OAuth spec. See below.


### Access Token Request

To request an access token, clients submit an authenticated POST to the `/auth/token` endpoint. This is per [OAuth].

The access token request must have a valid grant type - exchanging an authentication code for an access token uses the `authorization_code` grant type. Other OAuth grant types are not necessarily supported.

Note that this endpoint requires an authenticated client. The client may submit the client-id and secret in the form of an HTTP Basic authorization header, or it may use the `client_secret` post parameter described below.

Either way, unauthenticated clients will be rejected.

Part         | Value
------------:|------
Path         | `/auth/token/`
Authorization| Requires HTTP-Basic using a Client id and credential
Method       | `POST`
Query Params | None
Body         | Form parameters, as described below.

----
Form-encoded parameters:

Field           | Req? | Description
---------------:|------|------------
`grant_type`    | Req  | A valid OAuth grant type. This should be `authorization_code` for the code exchange scenario.
`code`          | Req  | A valid authorization code, as returned from the Authentication path. Required when `grant_type` is `authorization_code`
`client_id`     | Req  | Client identity (should match that in the Authorization header)
`client_secret` | Opt  | Client secret (optional mechanism rather than the Authorization header)
`code_type`     | Opt  | If set to `device_authorization`, the value of `code` will be treated as an SP-generated device authorization nonce.
`redirect_uri`  | Opt  | Redirect URI (needs to match that used in the Authorization Request)

### Access Token Response

A JSON-formatted document is returned if the access_token is granted.

Field          | Type   | Description
--------------:|--------|------------
`access_token` | String | An OAuth access token. Keep it secret, keep it safe.
`token_type`   | String | Per the OAuth spec [OAuth]. This will be `bearer` in the normal workflow.
`expires_in`   | Int    | Expiry time in seconds (value is 0 for tokens that don't expire)
`scope`        | String | comma-separated scope values for the authorization. As per the client configuration and initial authorization request.


### Access Token Errors

Token errors will be returned with one of the following HTTP response codes, and a JSON document as the document body.

HTTP Response | Sample cases
-------------:|-------------
`400`         | Missing or malformed request parameters. This includes invalid `access_code` usage.
`401`         | Invalid or missing client (application) credentials.
`419`         | The supplied `device_authorization` code is invalid - has already been used to authorize a device, or expired.

The document body will include the following:

Field               | Type   | Description
-------------------:|--------|------------
`error`             | String | Error description code
`error_description` | String | Error description, human-readable. Ish.


### Access Token Examples

Get an access token using the outcome of the authentication flow:

    $ curl -v -X POST
        --user 'testing-app-v010:secret'
        --data "redirect_uri=aerofs://redirect&grant_type=authorization_code&code=df652bbc-332e-4238-b8b8-1d1435f89086"
        'https://share.syncfs.com/auth/token'

    {"access_token":"60e7b2c4-8690-4121-af5f-a3a2156dd3b5", "token_type":"bearer","expires_in":0,"scope":"write,read"}

Submitting a device-authorization nonce:

    $ curl -v -X POST
        --user 'testing-app-v010:secret'
        --data "redirect_uri=aerofs://redirect&grant_type=authorization_code&code=df652bbc-332e-4238-b8b8-1d1435f89086&code_type=device_authorization"
        'https://share.syncfs.com/auth/token'

    {"access_token":"60e7b2c4-8690-4121-af5f-a3a2156dd3b5","token_type":"bearer","expires_in":0,"scope":"write,read"}

Minimal example for mobile client (whitespace added for clarity):

    $ curl -v -X POST --data "
        client_id=testing-app-v010
        &client_secret=secret
        &grant_type=authorization_code
        &code=df652bbc-332e-4238-b8b8-1d1435f89086
        &code_type=device_authorization"
          'https://share.syncfs.com/auth/token'

    {"access_token":"60e7b2c4-8690-4121-af5f-a3a2156dd3b5","token_type":"bearer","expires_in":0,"scope":"write,read"}


## Deleting a token

### Token Deletion Request

Access tokens are deleted by sending a `DELETE` to `/auth/token/<token>` where <token> is the access token to delete. No authentication is needed; knowledge of a token is considered proof of authority to delete the token.

Part         | Value
------------:|------
Path         | `/auth/token/<token>`
Authorization| None
Method       | `DELETE`
Query Params | None
Body         | Form parameters, as described below.

### Token Deletion Response

A response with status 200 and no content is returned on success.


### Token Deletion Errors

HTTP Response | Sample cases
-------------:|-------------
`404`         | The specified token was not found


## Verifying a token

Rather than construct self-verifying tokens (which still require server interaction), we provide simple tokens that can be verified by a call back to Bifrost.

### Verification Request

Verification requires a resource server key and credential to be provided as an HTTP-Basic authentication string.

Note that this endpoint requires authentication (in the form of HTTP-Basic auth using the resource-server's credentials).

Part         | Value
------------:|------
Path         | `/auth/tokeninfo`
Authorization| Required; HTTP-Basic Base64(<id>:<secret>)
Method       | `GET`
Query Params | As described below.
Body         | None.

----
Query parameters:

Parameter       | Req? | Description
---------------:|------|------------
`access_token`  | Req  | The access token to verify

### Verification Response

A JSON-formatted document is returned if the resource server authentication is valid and the request is well-formed.

Field          | Type           | Description
--------------:|----------------|------------
`audience`     | String         | The client for which the token was generated.
`scopes`       | array of String | Comma-separated names of granted scopes
`principal`    | Object         | The authenticated principal
`expires_in`   | Int            | The expiry time (in ... seconds?)

The principal object includes the following fields:

Field            | Type   | Description
----------------:|--------|------------
`name`           | String | Authenticated username
`roles`          | Array  | Array of role names (not populated)
`groups`         | Array  | Array of group names (not populated)
`admin_principal`| bool   | Not populated (FIXME)
`attributes`     | Array  | Array of attribute name-value pairs (not populated)

### Verification Error

Errors will be returned using an appropriate HTTP response code, and a JSON document. See [OAuth].

HTTP Response | Sample cases
-------------:|-------------
`400`         | Missing or invalid access token value.
`401`         | Invalid or missing resource server credentials.
`404`         | Access token does not exist.

The document body will include the following:

Field               | Type   | Description
-------------------:|--------|------------
`error`             | String | Error description code

### Verification Examples

    $ curl -v -X GET --user 'resource-server:secret'
        'https://share.syncfs.com/auth/tokeninfo?access_token=60e7b2c4-8690-4121-af5f-a3a2156dd3b5'

    {"audience":"Testing app client","scopes":["write","read"],
        "principal":{"name":"foo@example.com","roles":[],
            "groups":[],"admin_principal":false,"attributes":{}},
     "expires_in":0}

    $ curl  -v -X GET --user 'resource-server:secret'
        'https://share.syncfs.com/auth/tokeninfo?access_token=y_u_so_bogus'

    {"error":"not_found"}

# Mobile App Auth

Steps to authenticate a new mobile device:

1. User signs in to the AeroFS web site using existing authentication path.

2. User requests an authorization code for a mobile device by clicking on the
   "Add a Mobile Device" link. The web site makes an SP call to request a nonce:
        `spclient.RequestOneTimeAuth()`

3. SP generates a secure-enough random nonce and stores it securely. The nonce is associated
   with the logged-in User, and has a short expiry (measured in minutes).

4. Web code turns the nonce into a QR code along with whatever other context is needed.

5. The mobile app reads the QR code. It uses the nonce from the QR code as an access code and requests a token from Bifrost.

6. Bifrost returns an OAuth `access_token`
