<%inherit file="layout.mako"/>

<div class="span6 offset3">

    %if success:
        <h2>Please Check Your Email</h2>
        <div id="success_msg">
            If the email address you entered is associated with an AeroFS account,
            an email has been sent to that address with instructions to reset the password.
        </div>
    %else:
        <h2>AeroFS Password Reset</h2>
        <p>
            Please enter the email address you use with AeroFS. You will receive
            an email with a link to reset your password.
        </p>
        <form class="form-inline" action="${request.route_path('request_password_reset')}"
                method="post">
            ${self.csrf_token_input()}
            <input type="text" name="login" value=""/>
            <input class="btn" type="submit" name="form.submitted" value="Send Email"/>
        </form>
    %endif
</div>
