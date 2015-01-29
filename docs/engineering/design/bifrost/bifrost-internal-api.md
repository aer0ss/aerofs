# Bifrost addendum: Managing OAuth Clients and Listing Tokens

    Note: this API is not public; see docs/user-docs/bifrost_api.md for
    the public Bifrost API.

The client is the application that will be authorizing users and providing the access_token to a Resource Server as evidence-of-authority.

The client will receive a client id and secret credential that it must use when talking to Bifrost.

Managing client apps can only be performed by the site admin. Note that the following functions are not exposed outside the appliance; they are documented here for application developers that may need to know the internal element types. However, only Appliance-internal code that has already established authentication and authorization will perform these actions.


## Registering a Client


### Client Registration Request

Clients are registered with a POST to the `/clients` endpoint.

Part         | Value
------------:|------
Path         | `/clients/`
Authorization| None (handled externally)
Method       | `POST`
Query Params | None
Body         | Form parameters, as described below.

----
Form-encoded parameters:

Field           | Req? | Description
---------------:|------|------------
`resource_server_key` | Req | Unique key of the resource server with which the client will attempt to authorize (the resource server must already exist)
`client_name`   | Req   | Name of client to register
`redirect_uri`  | Req   | Callback URI to which the user is redirected after authorizing the client
`description`   | Opt   | Description of client
`contact_email` | Opt   | Contact email
`contact_name`  | Opt   | Contact name

### Client Registration Response

A JSON-formatted document is returned if client is successfully registered.

Field          | Type   | Description
--------------:|--------|------------
`client_id`     | String  | UUID of client
`secret`       | String | This, combined with a `client_id`, is valid authentication for requesting a new access_token


### Client Registration Errors

Errors will be reported with an appropriate HTTP error (400, 403, etc.). A JSON doc with "error" and "error_description" fields may be returned.

## Deleting a Client

A side-effect of this is the automatic revokation of all access_tokens associated with this client.

### Client Deletion Request

Clients are deleted with a DELETE to the `/clients` endpoint.

Part         | Value
------------:|------
Path         | `/clients/<client_id>`
Authorization| None (handled externally)
Method       | `DELETE`
Query Params | None
Body         | None


### Client Deletion Response

A response with a 200 code will be returned if the deletion is successful.


### Client Deletion Errors

Errors will be reported with an appropriate HTTP error (400, 403, etc.). A JSON doc with "error" and "error_description" fields may be returned.


## Requesting Info About a Specific Client

## Client Info Request

Information about a particular client is requested with a `GET` to `/clients/<client_id>`.

Part         | Value
------------:|------
Path         | `/clients/<client_id>`
Authorization| None (handled externally)
Method       | `GET`
Query Params | None
Body         | None

### Client Info Response

A JSON-formatted document is returned on success. The Client object includes some or all of the following fields. Optional fields that were not specified when the client was created will not be returned.

Field          | Type   | Description
--------------:|--------|------------
`client_id`    | String | Unique client ID
`resource_server_key` | String | Resource server unique key
`client_name`   | String    | Name of client
`redirect_uri`  | String    | Callback URI to which the user is redirected after authorizing the client
`description`   | String    | Description of client
`contact_email` | String    | Contact email
`contact_name`  | String    | Contact name


## Listing All Clients

### Client List Request

A client list is requested with a `GET` to the `/clients` endpoint.

Part         | Value
------------:|------
Path         | `/clients/`
Authorization| None (handled externally)
Method       | `GET`
Query Params | None
Body         | None


### Client List Response

A JSON-formatted document is returned on success.

Field          | Type   | Description
--------------:|--------|------------
`clients`      | Array of Client objects | The list of clients registered with the appliance

The Client object includes some or all of the following fields. Optional fields that were not specified when the client was created will not be returned.

Field          | Type   | Description
--------------:|--------|------------
`client_id`    | String | Unique client ID
`resource_server_key` | String | Resource server unique key
`client_name`   | String    | Name of client
`redirect_uri`  | String    | Callback URI to which the user is redirected after authorizing the client
`description`   | String    | Description of client
`contact_email` | String    | Contact email
`contact_name`  | String    | Contact name



### Client List Errors

Errors will be reported with an appropriate HTTP error (400, 403, etc.). A JSON doc with "error" and "error_description" fields may be returned.

## Creating an Authorization Code

To create an authorization code, submit a `POST` to the `/authorize` endpoint.

Part         | Value
------------:|------
Path         | `/authorize/`
Authorization| A nonce from SP (see form parameters)
Method       | `POST`
Query Params | None
Body         | Form params, as describe below

Form parameters:

Field       | Req | Description
-----------:|-----|------------
`response_type` | Req | Must be `code`
`client_id` | Req | Client ID
`nonce`     | Req | Proof-of-identity nonce, issued by SP
`redirect_uri` | Req | The callback URI
`scope`     | Opt | Comma-separate list of permissions
`state`     | Opt | If present, this value will be sent to the `redirect_uri` along with the `code`. The client should make this an unguessable string and check that the value in the response matches the value present here. If it does not, the response is from a third-party and should not be trusted.

### Response

See response to `GET /authorize` in the Bifrost user documentation.

## Listing all access tokens belonging to a user

### Access Token List Request

To list access tokens, clients submit a GET to the `/token` endpoint.

Part         | Value
------------:|------
Path         | `/tokenlist/`
Authorization| None (auth should be handled externally)
Method       | `GET`
Query Params | As described below
Body         | None

Query parameters:

Field          | Req?   | Description
--------------:|--------|------------
owner          | Req    | userid of the owner whose tokens should be listed

### Access Token List Response

A JSON-formatted document is returned on success.

Field         | Type                   | Description
-------------:|------------------------|------------
`tokens`      | Array of Token objects | The list of access_tokens belonging to the user

The Token object includes the following fields:

Field                 | Type   |  Description
---------------------:|--------|-------------
`client_id`           | String | UUID of the OAuth client with which the token is used
`client_name`         | String | Name of the OAuth client with which the token is used
`creation_date`       | Datetime | The datetime at which the token was issued
`expires`             | Long   | Token expiry date (in milliseconds since the epoch)
`token`               | String | The access token string

### Access Token List Errors

HTTP Response | Sample cases
-------------:|-------------
`400`         | `owner` query param is missing

