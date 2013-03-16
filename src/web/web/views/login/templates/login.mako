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
        <label for="inputEmail">Email:</label>
        <input class="input-medium" id="inputEmail" type="text" name="login"
            %if login:
                value="${login}"
            %endif
        >
        <label for="inputPasswd">Password:</label>
        <input class="input-medium" id="inputPasswd" type="password" name="password">
        <label class="checkbox">
            <input type="checkbox" name="staySignedIn" value="staySignedIn"
                   checked="checked"> Remember me
        </label>
        <input class="btn" type="submit" name="form.submitted" value="Sign In"/>
    </form>

    <p><a href="${request.route_path('request_password_reset')}">Forgot your password?</a></p>

</div>

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            $('#inputEmail').focus();
        });
    </script>
</%block>