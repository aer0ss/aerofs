IMPORTANT: Please read this document *before* contributing any Web code!

Forms
====

We use JavaScript to override the default behavior of many forms. However, if
the browser has Javascript disabled, the form will be submitted with the default
method GET, which encode all the fields the URL. the full URL can be displayed
and logged on both the client and the server. It is very bad if the URL contains
sensitive field data. Therefore, always specify method="POST" in all <form> tags.

  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
  @ Specify method="POST" in all <form> tags. @
  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@

TOOD (WW) check all forms to make sure POST is specified.


CSRF attack prevention
====

The server verifies CSRF tokens for all non-GET HTTP request (see
subscribers.py). Therefore, it's important that no request that modifies state
should use GET. Otherwise the request would bypass CSRF checking. Using GET to
modify state is also a bad design if not a bug. In other words, always specify

    request_method='POST'

or other non-GET methods in view callables.

  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
  @ No HTTP request that modifies system state should use GET. @
  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@

To generate CSRF tokens in requests, Use the following methods in layout.mako:

    o csrf_token_input()
    o csrf_token_param()

See the methods' documentation for detail.

When the user is not logged in, CSRF verification is disabled. Therefore, HTTP
requests that don't require logging in need no CSRF tokens (e.g. signup, signin
requests). In practice however, these requests can be made when the user has
already logged in. As a result, all non-GET requests must bear the CSRF token
even if they do not require logging in.

  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
  @ All non-GET requests must bear a CSRF token even if they do not @
  @ require logging in.                                             @
  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@


HTML escaping
====

TODO


References
====

OWASP is a good source of information about Web security: https://www.owasp.org/
