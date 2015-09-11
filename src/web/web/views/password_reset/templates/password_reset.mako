<%inherit file="marketing_layout.mako"/>
<%! page_title = "Reset Password" %>

<div class="row">
    <div class="col-sm-6 col-sm-offset-3">
        %if success:
            <h2>Password Updated</h2>
            <div id="success_msg">
                <p>Your password has been updated successfully.</p>
                <p>Click <a href="${request.route_path('login')}">here</a> to sign in with the new password.</p>
            </div>
        %else:

            %if error is None or not valid_password:
                <h2>Reset Password</h2>
                <div>Please enter a new password for <strong>${user_id}</strong>.</div>
                <form class="form-inline" action="${request.route_path('password_reset')}" method="post">
                    ## Note that the server doesn't always verify the CSRF token
                    ## since the users is usually logged off when changing the password.
                    ## This is okay as the request is secured by the token below.
                    ${self.csrf.token_input()}
                    <input type="hidden" name="token" value="${token}"/>
                    <input type="hidden" name="user_id" value="${user_id}"/>
                    <div class="input_container" style="margin-top: 20px">
                        <label for="password">New Password:</label>
                        <input type="password" id="password" name="password" value=""/>
                        <input class="btn btn-primary" type="submit" value="Update"/>
                    </div>
                </form>

            %elif password_already_exist:
                <h2>Cannot Reuse Current Password</h2>
                <p>
                    The new password you entered is the same as the current password. Please enter a different
                    password.
                </p>

                <form class="form-inline" action="${request.route_path('password_reset')}" method="post">
                    ## Note that the server doesn't always verify the CSRF token
                    ## since the users is usually logged off when changing the password.
                    ## This is okay as the request is secured by the token below.
                    ${self.csrf.token_input()}
                    <input type="hidden" name="token" value="${token}"/>
                    <input type="hidden" name="user_id" value="${user_id}"/>
                    <div class="input_container" style="margin-top: 20px">
                        <label for="password">New Password:</label>
                        <input type="password" id="password" name="password" value=""/>
                        <input class="btn btn-primary" type="submit" value="Update"/>
                    </div>
                </form>

            %else:
                <h2>Reset Password</h2>
                <div id="bad_token_error">
                    An error occurred while updating your password.  Please request password reset again
                    <a href="${request.route_path('request_password_reset')}">here</a>.
                    If you continue to have problems, please contact
                    <a href="http://support.aerofs.com">support</a>.
                </div>
            %endif
        %endif
    </div>
</div>

<%block name="scripts">
<script type="text/javascript">
    $(document).ready(function() {
        $("#password").focus();
    });
</script>
</%block>
