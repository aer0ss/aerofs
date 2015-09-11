<%inherit file="marketing_layout.mako"/>

%if password_expired:
    <%! page_title = "Password Expired" %>
%else:
    <%! page_title = "Reset Password" %>
%endif

<div class="row">
    <div class="col-sm-6 col-sm-offset-3">

        %if success:
            <h2>Please Check Your Email</h2>
            <div id="success_msg">
                If the email address you entered is associated with an AeroFS account,
                an email has been sent to that address with instructions to reset the password.
            </div>
        %else:
            %if password_expired:
                <h2>Password Expired</h2>
                <p>
                    Please enter the email address you use with AeroFS. You will receive
                    an email with a link to change your password.
                </p>
                <form class="form-inline" action="${request.route_path('request_password_reset')}"
                        method="post">
                    ${self.csrf.token_input()}
                    <input type="text" id="email-input" name="login" value="${userid}"/>
                    <input class="btn btn-default" type="submit" value="Send Email"/>
                </form>

            %else:
                <h2>Reset Password</h2>
                <p>
                    Please enter the email address you use with AeroFS. You will receive
                    an email with a link to reset your password.
                </p>
                <form class="form-inline" action="${request.route_path('request_password_reset')}"
                        method="post">
                    ${self.csrf.token_input()}
                    <input type="text" id="email-input" name="login" value=""/>
                    <input class="btn btn-default" type="submit" value="Send Email"/>
                </form>
            %endif

        %endif
    </div>
</div>

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            $("#email-input").focus();
        });
    </script>
</%block>
