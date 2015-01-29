# File Downloads

## Requirements

- user can click a link and download the file with the browser
- sensitive information is never present in the URL bar or in the href (in such a way that a user could copy/paste it without realizing that they're giving away sensitive information)
- file downloads should scale as well as the gateway/daemon can handle
- file downloads should not be susceptible to CSRF or other such vulnerabilities
- a user experiences sees no error when an OAuth token expires

## Solution

- the browser should request the file directly from the API gateway, rather than downloading it through a proxy like uWSGI which may not scale well (e.g. uWSGI does not scale well due to its limited thread-pool architecture)
- the file links that Shelob presents have a no-op href
- when a user clicks a file link, javascript directs the browser to /api/v1.0/files/:oid/content?token=:token, where :token is a short-lived OAuth token with "read" scope.
- for file downloads, a brand new OAuth token must be used, OR the token must be verified before use. If the js requests a file listing and receives a 401, it can request a new token and retry the file list call without the user's knowledge. However if the browser navigates directly to the API to download a file and receives a 401, the user has navigated away and Shelob cannot react. So, the token must be proven to be valid before the user is redirected.

## Why are we putting tokens in the URL? Isn't that bad?

There are three ways to pass information in a GET request:

1. in a header
2. in a cookie
3. in a query parameters

Option 1 is unsuitable because you cannot tell a browser to navigate to a URL with arbitrary headers. You can fetch the content in javascript, but this will not trigger a download of the file.

Option 2 is unsuitable because of the opportunity for CSRF attacks, since the API does not use CSRF tokens. A malicious webpage could link to the API and the browser could be confused into including a cookie which authorizes a transaction that the user never intended. This scenario could be prevented if the API required a secret CSRF token which a malicious website could not guess. See [Wikipedia](http://en.wikipedia.org/wiki/Cross-site_request_forgery#Prevention) for more information.

Option 3 it is, then! We limit the possible damage by using short-lived tokens (15 minutes).

