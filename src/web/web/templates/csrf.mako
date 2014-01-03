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
