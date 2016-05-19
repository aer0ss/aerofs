<%inherit file="marketing_layout.mako"/>
<%! page_title = "Log In" %>

<div class="row">
    <div class="col-sm-6 col-sm-offset-3 login">
        <h1>Log In to AeroFS</h1>
        %if external_login_enabled:
            <h6>If you have company credentials, please use them to log in
                <span data-toggle="tooltip" title="You can use the same email and password that you use for other services at your company.">
                    <img src="${request.static_path('web:static/img/question.png')}" class="tooltip-img">
                </span>
            </h6>
        %endif
        %if openid_enabled or saml_enabled:
            <div class="ext_auth-login">
                <div class="col-sm-8 col-sm-offset-2">
                    <a class="btn btn-primary btn-large btn-group-justified" href="${ext_auth_login_url}">Log in with ${ext_auth_service_identifier}</a>
                </div>
                %if ext_auth_display_user_pass_login:
                    <div class="col-sm-12 text-center login-divider" style="padding-top: 25px">
                        <span style="font-weight: bold;">
                            OR
                        </span>
                    </div>
                    <div class="col-sm-12 text-center">
                        <h3>${ext_auth_service_external_hint}</h3>
                    </div>
                %endif
            </div>
        %endif
        %if ext_auth_display_user_pass_login or not (openid_enabled or saml_enabled):
            <form id="login_form" class="form-horizontal" role="form" method="post">
                ${self.csrf.token_input()}
                <div class="form-group">
                    <label for="input_email" class="col-sm-4 control-label">Email</label>
                    <div class="col-sm-8">
                        <input class="input-medium form-control" id="input_email" type="email" name="${url_param_email}"
                            %if ext_auth_login:
                                value="${ext_auth_login}"
                            %endif
                        >
                    </div>
                </div>
                <div class="form-group">
                    <label for="input_passwd" class="col-sm-4 control-label">Password</label>
                    <div class="col-sm-8">
                        <input class="input-medium form-control" id="input_passwd" type="password" name="${url_param_password}">
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-sm-8 col-sm-offset-4">
                        %if not disable_remember_me:
                            <label class="checkbox">
                                <input type="checkbox" name="${url_param_remember_me}" value="staySignedIn"
                                       checked="checked"> Remember me
                            </label>
                        %endif
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-sm-8 col-sm-offset-4">
                        <input id="login_button" class="btn btn-primary" type="submit" value="Log In"/>
                    </div>
                </div>
            </form>

            <div class="row">
                <div class="col-sm-8 col-sm-offset-4"><a href="${request.route_path('request_password_reset')}">Forgot your password?</a></div>
                %if open_signup:
                    <br><br>
                    <div class="col-sm-8 col-sm-offset-4">
                        <a id="show-signup" href="#"
                            onclick="showSignup(); return false;">
                            Sign Up For An Account &#x25BE;</a>
                        <a id="hide-signup" href="#"
                            onclick="hideSignup(); return true;" style="display: none;">
                            I Already Have An Account &#x25B4;</a>
                    </div>
                %endif
            </div>
        %endif
    </div>
</div>

%if open_signup:
    <div class="row" id="signup" style="display: none;">
        <hr/>
        <div class="col-sm-6 col-sm-offset-3">
            <h1 align="center">Sign Up</h1>
            <form id="signup_form" class="form-horizontal" role="form" onsubmit="signup(); return false;">
                <div class="form-group">
                    <label for="signup_email" class="col-sm-4 control-label">Email</label>
                    <div class="col-sm-8">
                        <input class="input-medium form-control" id="signup_email" type="email" name="email">
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-sm-8 col-sm-offset-4">
                        <input id="signup_button" class="btn btn-primary" type="submit" value="Sign Up"/>
                    </div>
                </div>
            </form>
        </div>
    </div>

    <div id="email-sent-modal" class="modal" tabindex="-1" role="dialog">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <h4>Please check your email</h4>
                </div>
                <div class="modal-body">
                    <p>A confirmation email has been sent to <strong id="email-sent-address"></strong>.
                        Follow the instructions in the email to finish signing up the account.</p>
                    <p>Check your junk mail folder or try again if you don't see this email.</p>
                </div>
                <div class="modal-footer">
                    <a href="#" class="btn btn-primary" data-dismiss="modal">Close</a>
                </div>
            </div>
        </div>
    </div>
%endif

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            $('[data-toggle="tooltip"]').tooltip({
                placement: 'right',
                container : 'body'
            });

            %if login:
                ## focus on the password field if the email is already filled
                $('#input_passwd').focus();
            %else:
                $('#input_email').focus();
            %endif

            $('#login_form').submit(function() {
                $("#login_button").attr("disabled", "disabled");
                return true;
            });
        });

        %if open_signup:
            function signup() {
                var btn = $('#signup_button');
                setEnabled(btn, false);
                $.get("${request.route_path('json.request_to_signup')}",
                    $('#signup_form').serialize())
                .always(function() {
                    setEnabled(btn, true);
                }).done(function() {
                    hideAllModals();
                    $('#email-sent-modal').modal('show');
                    $('#email-sent-address').text($('#signup_email').val());
                }).fail(showErrorMessageFromResponse);
            }

            function showSignup() {
                $('#show-signup').hide();
                $('#hide-signup').show();
                $('#signup').show();
            }

            function hideSignup() {
                $('#hide-signup').hide();
                $('#show-signup').show();
                $('#signup').hide();
            }
        %endif
    </script>
</%block>
