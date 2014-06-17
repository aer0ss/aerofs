<%inherit file="marketing_layout.mako"/>
<%! page_title = "Sign In" %>

<div class="row">
    <div class="col-sm-4 col-sm-offset-4 login">
        <h1>Sign In to AeroFS
            %if not is_private_deployment:
                Hybrid Cloud
            %endif
        </h1>
        %if openid_enabled:
        <div class="openid-login">
            <div class="col-sm-12">
                <a class="btn btn-primary btn-large" href="${openid_url}">Sign in with ${openid_service_identifier}</a>
            </div>
            <div class="col-sm-12 text-center login-divider">
                <span>
                    OR
                </span>
            </div>
            <div class="col-sm-12 text-center">
                <h3>${openid_service_external_hint}</h3>
            </div>
        </div>
        %endif

            ## N.B. signup.mock manually creates this form. Make sure the fields there
            ## are consistent with the fields here.

            <form id="signin_form" class="form-horizontal" role="form" action="${request.url}" method="post">
                ${self.csrf.token_input()}
                <div class="form-group">
                    <label for="input_email" class="col-sm-4 control-label">Email</label>
                    <div class="col-sm-8">
                        <input class="input-medium form-control" id="input_email" type="email" name="${url_param_email}"
                            %if login:
                                value="${login}"
                            %endif
                        >
                    </div>
                </div>
                <div class="form-group">
                    <label for="input_passwd" class="col-sm-4 control-label">Password</label>
                    <div class="col-sm-8">
                        <input class="input-medium form-control" id="input_passwd" type="password" name="${url_param_password}">
                    <div class="col-sm-8">
                </div>
                <div class="form-group">
                    <div class="col-sm-12">
                        <label class="checkbox">
                            <input type="checkbox" name="${url_param_remember_me}" value="staySignedIn"
                                   checked="checked"> Remember me
                        </label>
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-sm-12">
                        <input id="signin_button" class="btn btn-primary" type="submit" value="Sign In"/>
                    </div>
                </div>
            </form>

            <div class="row">
                <div class="col-sm-12"><a href="${request.route_path('request_password_reset')}">Forgot your password?</a></div>
            </div>
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
