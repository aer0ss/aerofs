<%inherit file="marketing_layout.mako"/>
<%! page_title = "Create Account" %>

<div class="row">
    <div class="col-sm-6 col-sm-offset-3 text-center">
        <h1>Create an AeroFS account</h1>
    </div>
</div>

<div class="row">
    <div class="col-sm-6 col-sm-offset-3">
        <div class="well well-small text-center" style="margin: 0 auto;">
            You will sign up using this email:<br>
            <strong>${email_address}</strong>
        </div>
        <br>
    </div>
</div>

<div class="row">
    <div class="col-sm-6 col-sm-offset-3">
                ## Always use POST to avoid disclose passwords in the URL when JS is disabled.
                <form id="signupForm" method="post" role="form" class="form-horizontal">
                    ${self.csrf.token_input()}
                    ## signup.py needs the email address only to generate scrypt
                    ## credentials. Alternatively, we can query the SP for the email address
                    ## but this would add an extra round trip to SP.
                    <input type="hidden" name="${url_param_email}" value="${email_address}"/>
                    <input type="hidden" name="${url_param_signup_code}" value="${code}"/>
                    <div class="form-group">
                        <label for="inputFirstName" class="col-sm-4">First name: *</label>
                        <div class="col-sm-8">
                            <input id="inputFirstName" class="form-control" type="text" name="${url_param_first_name}" required>
                        </div>
                    </div>
                    <div class="form-group">
                        <label for="inputLastName" class="col-sm-4">Last name: *</label>
                        <div class="col-sm-8">
                            <input id="inputLastName" class="form-control" type="text" name="${url_param_last_name}" required>
                        </div>
                    </div>

                    <div class="form-group">
                        <label for="inputPasswd" class="col-sm-4">Create password: *</label>
                        <div class="col-sm-8">
                            <input id="inputPasswd" class="form-control" type="password" name="${url_param_password}">
                        </div>
                    </div>
                    <div class="form-group">
                        <div class="col-sm-8 col-sm-offset-4">
                            <span class="help-block">
                                Fields marked by (*) are mandatory.<br/><br/>
                                By signing up, you agree to AeroFS's 
                                <a href="https://www.aerofs.com/terms/#privatecloud" target="_blank">Terms of Service</a>
                            </span>
                        </div>
                    </div>

                    <div class="form-group">
                        <div class="col-sm-8 col-sm-offset-4">
                            <button id="submitButton" class="btn btn-primary" type="submit">Sign Up</button>
                        </div>
                    </div>
                </form>
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
                        sign_in();
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

            // after creating account, log user in.
            var sign_in = function() {
                ## create the login form
                var form = document.createElement("form");
                form.setAttribute("method", "post");
                ## redirect to the install page right after signing up
                form.setAttribute("action", "${request.route_path('login',
                            _query={'next': request.route_path('download',
                                           _query={'msg_type': 'signup'}) })}");
                var params = {
                    ${self.csrf.token_param()}
                    "${url_param_email}": "${email_address}",
                    "${url_param_password}": $("#inputPasswd").val(),
                    "${url_param_remember_me}": ""
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
