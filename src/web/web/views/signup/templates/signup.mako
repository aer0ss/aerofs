<%inherit file="marketing_layout.mako"/>
<%! page_title = "Create Account" %>

<div class="row">
    <div class="span12 text-center">
        <h1>Create an AeroFS account</h1>
    </div>
</div>

<div class="row">
    <div class="span4 offset4">
        <div class="well well-small text-center" style="margin: 0 auto;">
            You will sign up using this email:<br>
            <strong>${email_address}</strong>
        </div>
        <br>
    </div>
</div>

<div class="row">
    <div class="span8 offset4">
        <div class="row-fluid">
            <div class="span8 offset1">
                ## Always use POST to avoid disclose passwords in the URL when JS is disabled.
                <form id="signupForm" method="post">
                    ${self.csrf.token_input()}
                    ## signup.py needs the email address only to generate scrypt
                    ## credentials. Alternatively, we can query the SP for the email address
                    ## but this would add an extra round trip to SP.
                    <input type="hidden" name="${url_param_email}" value="${email_address}"/>
                    <input type="hidden" name="${url_param_signup_code}" value="${code}"/>
                    <label for="inputFirstName">First name: *</label>
                    <input class="span6" id="inputFirstName" type="text" name="${url_param_first_name}">
                    <label for="inputLastName">Last name: *</label>
                    <input class="span6" id="inputLastName" type="text" name="${url_param_last_name}">

                    <div
                    %if is_private_deployment:
                        class="hidden"
                    %endif
                    >
                        <label for="inputTitle">Job Title:</label>
                        <input class="span6" id="inputTitle" type="text" name="${url_param_title}">
                        <label for="inputCompany">Company:</label>
                        <input class="span6" id="inputCompany" type="text" name="${url_param_company}">
                        <label for="inputCompanySize">Size:</label>
                        <input class="span6" id="inputCompanySize" type="text" name="${url_param_company_size}">
                        <label for="inputCountry">Country:</label>
                        <input class="span6" id="inputCountry" type="text" name="${url_param_country}">
                        <label for="inputPhone">Phone:</label>
                        <input class="span6" id="inputPhone" type="text" name="${url_param_phone}">
                    </div>

                    <label for="inputPasswd">Create password: *</label>
                    <input class="span6" id="inputPasswd" type="password" name="${url_param_password}">

                    <span class="help-block footnote" style="margin-top: 10px;">
                        Fields marked by (*) are mandatory.<br/><br/>
                        By signing up you agree to AeroFS <a href="${request.route_path('terms')}#tos" target="_blank">Terms of Service</a>
                    </span>

                    <button id="submitButton" class="btn btn-primary" type="submit">Sign Up</button>
                </form>
            </div>
        </div>
    </div>
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

                        if (analytics) {
                            analytics.identify(response['email_address']);

                            ## Wait 300 ms for the Analytics call to succeed and then proceed to sign in.
                            ## This is the delay they recommend in their track_forms API.
                            ## See: https://mixpanel.com/docs/integration-libraries/javascript-full-api#track_forms
                            setTimeout(sign_in, 300);
                        } else {
                            ## no analytics, proceed to sig-in directly
                            sign_in();
                        }
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
                    ## redirect to the install page right after signing up
                    "${url_param_next}": "${request.route_path('download',
                        _query={'msg_type': 'signup'})}",
                    "${url_param_email}": "${email_address}",
                    "${url_param_password}": $("#inputPasswd").val(),
                    "${url_param_remember_me}": "",
                    "${url_param_form_submitted}": ""
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
