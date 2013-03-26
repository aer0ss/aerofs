<%inherit file="layout.mako"/>

<div class="span12">
    <h1 style="text-align: center">Create an account</h1>
</div>

<div class="span4 offset4">
    <div class="well well-small" style="margin: 0 auto;">
        <div style="text-align: center;">
            You will sign up using this email:<br>
            <strong>${email_address}</strong>
        </div>
    </div>
    <br>
</div>

<div class="span7 offset5">
    ## Always use POST to avoid disclose passwords in the URL when JS is disabled.
    <form id="signupForm" method="post">
        ${self.csrf.token_input()}
        ## signup.py needs the email address only to generate scrypt
        ## credentials. Alternatively, we can query the SP for the email address
        ## but this would add an extra round trip to SP.
        <input type="hidden" name="${url_param_email}" value="${email_address}"/>
        <input type="hidden" name="${url_param_signup_code}" value="${code}"/>
        <label for="inputFirstName">First name:</label>
        <input class="span2" id="inputFirstName" type="text" name="${url_param_first_name}">
        <label for="inputLastName">Last name:</label>
        <input class="span2" id="inputLastName" type="text" name="${url_param_last_name}">
        <label for="inputPasswd">Password:</label>
        <input class="span2" id="inputPasswd" type="password" name="${url_param_password}">

        <span class="help-block footnote" style="margin-top: 10px;">
            By signing up you agree to AeroFS <a href="http://www.aerofs.com/tos" target="_blank">Terms of Service</a>
        </span>

        <button id="submitButton" class="btn btn-primary" type="submit">Sign Up</button>
    </form>
</div>

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            ## set focus on the first name field
            $("#inputFirstName").focus();

            ## The following code is copied from
            ## http://stackoverflow.com/questions/5004233/jquery-ajax-post-example
            var request;
            $("#signupForm").submit(function(event) {
                ## abort pending request
                if (request) request.abort();

                var $form = $(this);

                ## Serialize data *before* disabling form inputs, since disabled
                ## inputs are excluded from serialized data.
                var serializedData = $form.serialize();

                var $submitButton = $("#submitButton");
                $submitButton.attr("disabled", "disabled");

                $.post("${request.route_path('json.signup')}",
                    serializedData
                )
                .done(function (response, textStatus, jqXHR) {
                    var error = response['error'];
                    if (error) {
                        displayError(error);
                    } else {
                        ## automatically sign in once the AJAX call succeeds
                        mixpanel.alias("${email_address}");
                        mixpanel.name_tag("${email_address}");
                        ## TODO (WW) we will be stuck if track() fails. But I don't have a good solution
                        mixpanel.track("Signed Up", {}, function() {
                            sign_in();
                        });
                    }
                })
                .fail(function (jqXHR, textStatus, errorThrown) {
                    displayError("Error: " + errorThrown);
                });

                ## prevent default posting of form
                event.preventDefault();

                function displayError(error) {
                    showErrorMessage(error);
                    $submitButton.removeAttr("disabled");
                }
            });

            function sign_in() {
                ## create the login form
                var form = document.createElement("form");
                form.setAttribute("method", "post");
                form.setAttribute("action", "${request.route_path('login')}");

                var params = {
                    ${self.csrf.token_param()}
                    ${url_param_next} : "${next}",
                    ${url_param_email} : "${email_address}",
                    ${url_param_password} : $("#inputPasswd").val(),
                    ${url_param_remember_me}: "",
                    ${url_param_form_submitted} : ""
                };

                for (var key in params) {
                    var hiddenField = document.createElement("input");
                    hiddenField.setAttribute("type", "hidden");
                    hiddenField.setAttribute("name", key);
                    hiddenField.setAttribute("value", params[key]);
                    form.appendChild(hiddenField);
                }

                document.body.appendChild(form);
                form.submit();
            }
        });
    </script>
</%block>
