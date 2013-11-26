## N.B. Please read README.security.txt carefully before proceeding.
##
## All forms should call this function to append the CSRF token to each POST request:
##
##  <form method="post">${self.csrf.token_input()} ...</form>
##
## The name of the field must be identical to the one in csrf.py
##
<%def name="token_input()">
    <input type="hidden" name="csrf_token" value="${request.session.get_csrf_token()}"/>
</%def>

## N.B. Please read README.security.txt carefully before proceeding.
##
## All non-GET AJAX requests should call this function to append the CSRF token:
##
##  $.post(url, {
##      ${self.csrf.token_param()},
##      ...
##  });
##
## The name of the field must be identical to the one in csrf.py
##
<%def name="token_param()">
    "csrf_token": "${request.session.get_csrf_token()}",
</%def>

<%def name="token_header()">"X-CSRF-Token": "${request.session.get_csrf_token()}"</%def>