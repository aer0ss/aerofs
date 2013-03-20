<%inherit file="layout.mako"/>

<div class="span12">
    <h1 style="text-align: center">Sign In to AeroFS</h1>
</div>
<div class="span7 offset5">
    <br>

    ## N.B. signup.mock manually creates this form. Make sure the fields there
    ## are consistent with the fields here.

    <form action="${request.route_path('login')}" method="post">
        ${self.csrf.token_input()}
        <input type="hidden" name="next" value="${next}"/>
        <label for="input_email">Email:</label>
        <input class="input-medium" id="input_email" type="text" name="login"
            %if login:
                value="${login}"
            %endif
        >
        <label for="input_passwd">Password:</label>
        <input class="input-medium" id="input_passwd" type="password" name="password">
        <label class="checkbox">
            <input type="checkbox" name="stay_signed_in" value="staySignedIn"
                   checked="checked"> Remember me
        </label>
        <input class="btn" type="submit" name="form_submitted" value="Sign In"/>
    </form>

    <p><a href="${request.route_path('request_password_reset')}">
        Forgot your password?</a></p>

</div>

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            %if login:
                ## focus on the password field if the email is already filled
                $('#input_passwd').focus();
            %else:
                $('#input_email').focus();
            %endif

        });
    </script>
</%block>