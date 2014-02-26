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
                    <input class="span6" id="inputFirstName" type="text" name="${url_param_first_name}" required>
                    <label for="inputLastName">Last name: *</label>
                    <input class="span6" id="inputLastName" type="text" name="${url_param_last_name}" required>

                    <div
                    %if is_private_deployment:
                        class="hidden"
                    %endif
                    >
                        <label for="inputPhone">Phone: *</label>
                        <input class="span6" id="inputPhone" type="text" name="${url_param_phone}"
                        %if not is_private_deployment:
                        required
                        %endif
                        >

                        <label for="inputCompany">Company:</label>
                        <input class="span6" id="inputCompany" type="text" name="${url_param_company}">
                        <label for="inputTitle">Job Title:</label>
                        <input class="span6" id="inputTitle" type="text" name="${url_param_title}">
                        <label for="inputCompanySize">Company Size:</label>
                        <input class="span6" id="inputCompanySize" type="text" name="${url_param_company_size}">

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
    <script src="${request.static_path('web:static/js/jquery.validate.min.js')}"></script>

    <script type="text/javascript">
        $("#signupForm").validate({ errorClass: 'error'});

        $(document).ready(function() {
            ## set focus on the first name field
            $("#inputFirstName").focus();

            ## The following code is copied from
            ## http://stackoverflow.com/questions/5004233/jquery-ajax-post-example
            var request;
            $("#signupForm").submit(function(event) {
                if (!$("#signupForm").valid()) return false;
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

                            ## post to Pardot using hidden iframe tag so that the user is properly cookied
                            ## this technique is recommended by pardot @ http://www.pardot.com/faqs/forms/form-handlers/
                            $('body').append("<iframe id=\"pdiframe\" src=\"https://go.pardot.com/l/32882/2014-02-26/5m2y?first_name=" + encodeURIComponent(response['firstName']) +
                                                "&last_name=" + encodeURIComponent(response['lastName']) +
                                                "&email=" + encodeURIComponent(response['email_address']) +
                                                "&company=" + encodeURIComponent(response['company']) +
                                                "&company_size=" + encodeURIComponent(response['employees']) +
                                                "&phone=" + encodeURIComponent(response['phone'])
                                                +"\" width='1' height='1'></iframe>;");

                            ## need to wait for pardot to finish loading
                            ## before proceeding with signup. We have a lot of marketing dependencies on pardot
                            ## so need to make sure the iframe fully loads and the data is passed into pardot before going on.

                            $('iframe#pdiframe').load(function() {

                                ## Wait 300 ms for the Analytics call to succeed and then proceed to sign in.
                                ## This is the delay they recommend in their track_forms API.
                                ## See: https://mixpanel.com/docs/integration-libraries/javascript-full-api#track_forms

                                analytics.identify(response['email_address'],
                                    {
                                        'email': response['email_address'],
                                        'firstName': response['firstName'],
                                        'lastName': response['lastName'],
                                        'company': response['company'],
                                        'employees': response['employees'],
                                        'phone': response['phone']

                                    });
                                analytics.track('Signed Up for AeroFS Hybrid Cloud');
                                setTimeout(sign_in, 300);
                            });

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
                    ${self.csrf.token_param()}
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
