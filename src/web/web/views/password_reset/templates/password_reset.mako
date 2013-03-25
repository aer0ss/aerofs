<%inherit file="layout.mako"/>

<div class="span6 offset3">
    <h2>AeroFS Password Reset</h2>

    %if success:
        <div id="success_msg">
            <p>Your password has been changed.</p>
            <p>Click <a href="${request.route_path('login')}">here</a> to sign in with the new password.</p>
        </div>
    %else:
        %if error == None or not valid_password:
            <div>Hi <strong>${user_id}</strong>, please enter your new password.</div>
            <form class="form-inline" action="${request.route_path('password_reset')}" method="post">
                ## Note that the server doesn't always verify the CSRF token
                ## since the users is usually logged off when changing the password.
                ## This is okay as the request is secured by the token below.
                ${self.csrf.token_input()}
                <input type="hidden" name="token" value="${token}"/>
                <input type="hidden" name="user_id" value="${user_id}"/>
                <div class="input_container">
                    <label for="password">New Password:</label>
                    <input type="password" id="password" name="password" value=""/>
                    <input class="btn" type="submit" name="form.submitted" value="Update"/>
                </div>
            </form>
        %else:
            <div id="bad_token_error">
                An error occurred while updating your password.  Please request password reset again
                <a href="${request.route_path('request_password_reset')}">here</a>.
                If you continue to have problems, please contact
                <a href="http://support.aerofs.com">support</a>.
            </div>
        %endif
    %endif
</div>

<%block name="scripts">
<script type="text/javascript">
    $(document).ready(function() {
        $("#password").focus();
    });
</script>
</%block>