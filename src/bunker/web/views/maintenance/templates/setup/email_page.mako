<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="setup_common.mako"/>

<%! from web.util import str2bool %>
<%! from web.views.maintenance.maintenance_util import unformat_pem %>

<%
    public_host_name = current_config['email.sender.public_host']
    # Use the local namespace so the method scripts() can access it
    local.is_remote_host = public_host_name != "" and public_host_name != "postfix.service"

    # TODO (WW) generate the string in the Python view after splitting
    # _setup_common() page specific views.
    import random
    local.verification_code = str(random.randint(1000, 9999))
%>

<h4>Email server:</h4>

<form method="post" role="form" onsubmit="submitForm(); return false;">
    <div class="row form-group">
        <div class="col-sm-12">
        ${csrf.token_input()}
        <div class="radio">
            <label>
                <input type='radio' name='email-server' value='local'
                       onchange="localMailServerSelected()"
                   %if not local.is_remote_host:
                       checked="checked"
                   %endif
                >
                Use the AeroFS Appliance's local mail relay
            </label>
        </div>

        <div class="radio">
            <label>
                <input type='radio' name='email-server' value='remote'
                       onchange="externalMailServerSelected()"
                    %if local.is_remote_host:
                       checked="checked"
                    %endif
                >
                Use external mail relay:

                ## The slide down options
                <div id="public-host-options" class="form-group slide-down-options" 
                    %if not local.is_remote_host:
                        style="display: none;"
                    %endif
                >

                    <div class="inline-form">
                        <div class="row form-group">
                            <label for="email-sender-public-host" class="col-sm-3">SMTP host:</label>
                            <input id="email-sender-public-host" name="email-sender-public-host" type="text" class="form-control"
                                   ## We don't want to show "localhost" as the remote host
                                   ## if the local mail relay is used.
                                   value="${public_host_name if local.is_remote_host else ''}">

                            <%
                                # We don't want to show the local relay's port as the
                                # remote port if the local mail relay is used.
                                val = current_config['email.sender.public_port'] \
                                    if local.is_remote_host else ''
                                if not val: val = '25'
                            %>
                            <label for="email-sender-public-port">Port:</label>
                            <input id="email-sender-public-port" class="form-control" name="email-sender-public-port" type="text" size="5" length="5" value="${val}">
                        </div>

                        <div class="row form-group">
                            <div class="col-sm-3">
                                <label for="email-sender-public-username">Username:</label>
                            </div>
                            <input id="email-sender-public-username" class="form-control col-sm-6" name="email-sender-public-username" type="text" value="${current_config['email.sender.public_username']}">
                        </div>
                        <div class="row form-group">
                            <label for="email-sender-public-password" class="col-sm-3">Password:</label>
                            <input id="email-sender-public-password" name="email-sender-public-password" type="password" class="form-control col-sm-6" value="${current_config['email.sender.public_password']}">
                        </div>
                        <div class="row form-group">
                            <div class="col-sm-6 col-sm-offset-3">
                                <div class="checkbox tls-checkbox">
                                    <label for="email-sender-public-enable-tls">
                                        <input id="email-sender-public-enable-tls" name="email-sender-public-enable-tls" type="checkbox" onchange="toggleCertificate(this)"
                                            %if str2bool(current_config['email.sender.public_enable_tls']):
                                                checked
                                            %endif
                                        >Use STARTTLS encryption
                                    </label>
                                </div>
                                <div id="certificate-options"
                                    %if not str2bool(current_config['email.sender.public_enable_tls']):
                                        style="display: none;"
                                    %endif
                                >
                                    ${certificate_options()}
                                </div>
                            </div>
                        </div>
                    </div>

                </div>
            </label>
        </div>
        <p class="help-block">AeroFS sends emails to users for various purposes such as sign up verification and folder invitations. A functional email server is required.</p>
        </div>
    </div>


    <div class="row form-group">
        <%
            val = current_config['base.www.support_email_address']
            if not val: val = default_support_email
        %>
        <div class="col-sm-12">
            <div class=""><label for="base-www-support-email-address">Support email address:</label></div>
                <input id="base-www-support-email-address" class="form-control" name="base-www-support-email-address" type="text" value=${val}>

            <p class="help-block">This email address is used for all "Support" links and as the
                "From" field for emails sent out by the system. Set it to an email address
                you want users to send support requests to, e.g. <strong>support@acme.com</strong>.</p>
        </div>
    </div>
    ${common.render_next_prev_buttons()}
</form>

<div id="verify-modal-email-input" class="modal tabindex="-1" role="dialog">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal">&times;</button>
                <h4>Test SMTP settings</h4>
            </div>

            <div class="modal-body">
                <p>Please test SMTP settings before proceeding.</p>
                <p>Enter your email address and we will send you a test email.</p>
                <form id="verify-modal-email-input-form" method="post" class="form-inline"
                        onsubmit="sendVerificationCodeAndShowCodeInputModal(); return false;">
                    ${csrf.token_input()}
                    <input type="hidden" name="verification-code" value="${local.verification_code}"/>
                    <label for="verification-to-email">Email address:</label>
                    <input id="verification-to-email" name="verification-to-email" type="text"
                           value="${current_config['last_smtp_verification_email']}">
                </form>
            </div>
            <div class="modal-footer">
                <a href="#" class="btn btn-default" data-dismiss="modal">Close</a>
                <a href="#" id="send-verification-code-button" class="btn btn-primary"
                   onclick="sendVerificationCodeAndShowCodeInputModal(); return false;">Send verification code</a>
            </div>
        </div>
    </div>
</div>

<div id="verify-modal-code-input" class="modal" tabindex="-1" role="dialog">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal">&times;</button>
                <h4>Enter test verification code</h4>
            </div>

            <div class="modal-body">
                <p>Please enter the verification code you received in the test email.</p>
                <form id="verify-modal-code-iput-form" method="post" class="form-inline"
                        onsubmit="checkVerificationCodeAndSetConfiguration(); return false;">
                    <label for="verification-code">Verification code:</label>
                    <input id="verification-code" name="verification-code" type="text">
                </form>
                <br>
                <p>Need help troubleshooting? Consult our <a href="https://support.aerofs.com/hc/en-us/articles/204862440" target="_blank">help center documentation</a>.</p>
            </div>
            <div class="modal-footer">
                <a href="#" class="btn btn-default" data-dismiss="modal">Close</a>
                <a href="#" id="continue-button" class="btn btn-primary"
                    onclick="checkVerificationCodeAndSetConfiguration(); return false;">
                    Verify</a>
            </div>
        </div>
    </div>
</div>

<div id="verify-succeed-modal" class="modal" tabindex="-1" role="dialog">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h4 class="text-success">Test succeeded</h4>
            </div>

            <div class="modal-body">
                <p>Great! Your email server works. Let's move on to the next page.</p>
            </div>
            <div class="modal-footer">
                <a href="#" class="btn btn-primary" id="continue-btn"
                    onclick="gotoNextPage(); return false;">
                    Continue</a>
            </div>
        </div>
    </div>
</div>

<%def name="certificate_options()">
<div class="row form-group">
        <label for="email-sender-public-cert" class="">Server certificate for StartTLS (optional):</label>
</div>
<div class="row form-group">
        <div class="">
            <textarea rows="4" id="email-sender-public-cert" class="form-control" style="width: 100%;" name="email-sender-public-cert"
                >${unformat_pem(current_config['email.sender.public_cert'])}</textarea>
                ## Also see setup_view.py:_format_pem() for the reversed convertion.
            <div class="help-block">This setting is not required if your mail server
                certificate is signed by a common certificate authority.</div>
        </div>
</div>
</%def>
<%def name="scripts()">
    <script src="${request.static_path('web:static/js/purl.js')}"></script>

    <script>
        $(document).ready(function() {
            $('#verify-modal-email-input').on('shown.bs.modal', function() {
                $('#verification-to-email').focus();
                setEnabled($('#send-verification-code-button'), true);
            });

            $('#verify-modal-code-input').on('shown.bs.modal', function() {
                $('#verification-code').focus();
                setEnabled($('#continue-button'), true);
            });

            ## Focus on the support email input if it's empty
            var $support_email = $('#base-www-support-email-address');
            if (!$support_email.val()) $support_email.focus();
        });

        function localMailServerSelected() {
            $('#public-host-options').hide();
        }

        function externalMailServerSelected() {
            $('#public-host-options').show();
        }

        function toggleCertificate(obj) {
            if (obj.checked) {
                $('#certificate-options').show();
            } else {
                $('#certificate-options').hide();
            }
        }

        var serializedData;
        function submitForm() {
            serializedData = $('form').serialize();

            if (!verifyPresence("base-www-support-email-address",
                        "Please specify a support email address.")) return;

            var remote = $("input[name='email-server']:checked").val() == 'remote';
            if (remote && (
                    !verifyPresence("email-sender-public-host", "Please specify SMTP host.") ||
                    !verifyPresence("email-sender-public-port", "Please specify SMTP port."))) {
                return;
            }

            var host = $("#email-sender-public-host").val();
            var port = $("#email-sender-public-port").val();
            var username = $("#email-sender-public-username").val();
            var password = $("#email-sender-public-password").val();
            var enable_tls = $("#email-sender-public-enable-tls").is(':checked');
            var certificate = $("#email-sender-public-cert").val();

            var current_host = "${current_config['email.sender.public_host']}";
            var current_port = "${current_config['email.sender.public_port']}";
            var current_username = "${current_config['email.sender.public_username']}";
            var current_password = "${current_config['email.sender.public_password']}";
            var current_enable_tls = ${str(str2bool(current_config['email.sender.public_enable_tls'])).lower()};
            var current_certificate = "${current_config['email.sender.public_cert']}";

            ## This is used by CI to skip verification during automated testing
            var skipEmailVerification = $.url().param('skip_email_verification');

            if (!skipEmailVerification) {
                ## As we are showing modals, do not disable nav buttons
                showVerifyEmailInputModal();

            } else {
                disableNavButtons();

                doPost("${request.route_path('json_setup_email')}",
                    serializedData, gotoNextPage, enableNavButtons);
            }
        }

        function sendVerificationCodeAndShowCodeInputModal() {
            if (!verifyPresence("verification-to-email",
                        "Please specify an email address.")){
                            hideAllModals();
                            return;
                        }

            var $btn = $('#send-verification-code-button');
            setEnabled($btn, false);

            var data = serializedData +
                    '&' + $('#verify-modal-email-input-form').serialize();
            doPost("${request.route_path('json_verify_smtp')}", data,
                    showVerifyCodeInputModal, function() {
                        setEnabled($btn, true);
                        hideAllModals();
                    });
        }

        function showVerifyEmailInputModal() {
            hideAllModals();
            $('#verify-modal-email-input').modal('show');
        }

        function showVerifyCodeInputModal() {
            hideAllModals();
            $('#verify-modal-code-input').modal('show');
        }

        function checkVerificationCodeAndSetConfiguration() {
            var inputtedCode = $("#verification-code").val();

            if (inputtedCode == '${local.verification_code}') {
                ## The button will be enabled next time the dialog shows.
                ## (The dialog is always dismissed after the doPost() call.)
                setEnabled($('#continue-button'), false);
                doPost("${request.route_path('json_setup_email')}",
                    serializedData, showVerificationSuccessModal, hideAllModals);
            } else {
                showAndTrackErrorMessage("The verification code you provided was not correct.");
            }
        }

        function showVerificationSuccessModal() {
            hideAllModals();
            $('#verify-succeed-modal').modal('show');
        }
    </script>
</%def>

