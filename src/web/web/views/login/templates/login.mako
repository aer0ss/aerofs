<%inherit file="marketing_layout.mako"/>
<%! page_title = "Sign In" %>

<div class="row">
    <div class="span12" style="margin-bottom: 40px">
        <h1 style="text-align: center">Sign In to AeroFS</h1>
    </div>
    %if openid_enabled:
        <div class="span12 text-center">
            <a class="btn btn-primary btn-large" href="${openid_url}">Sign In with OpenID</a>
        </div>
        <div class="span10 offset1" style="border-top: 1px solid #d3d3d3;
        margin-top: 60px; margin-bottom: 40px"></div>
        <div class="span12 text-center">
            <h3>${openid_service_external_hint}</h3>
        </div>
    %endif

    <div class="span7 offset5">
        ## N.B. signup.mock manually creates this form. Make sure the fields there
        ## are consistent with the fields here.

        <form id="signin_form" action="${request.route_path('login')}" method="post">
            ${self.csrf.token_input()}
            <input type="hidden" name="${url_param_next}" value="${next}">
            <input type="hidden" name="${url_param_form_submitted}" value="1">
            <label for="input_email">Email</label>
            <input class="input-medium" id="input_email" type="text" name="${url_param_email}"
                %if login:
                    value="${login}"
                %endif
            >
            <label for="input_passwd">Password</label>
            <input class="input-medium" id="input_passwd" type="password" name="${url_param_password}">
            <label class="checkbox">
                <input type="checkbox" name="${url_param_remember_me}" value="staySignedIn"
                       checked="checked"> Remember me
            </label>
            <input id="signin_button" class="btn btn-primary" type="submit" value="Sign In"/>
        </form>

        <a href="${request.route_path('request_password_reset')}">Forgot your password?</a>
    </div>
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

            $('#signin_form').submit(function() {
                $("#signin_button").attr("disabled", "disabled");
                return true;
            });
        });
    </script>
</%block>
