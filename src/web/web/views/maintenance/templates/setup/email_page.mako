<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

<%! from web.util import str2bool %>

<%
    public_host_name = current_config['email.sender.public_host']
    # Use the local namespace so the method scripts() can access it
    local.is_remote_host = public_host_name != "" and public_host_name != "localhost"
%>

<h4>Email server:</h4>

<form method="POST" onsubmit="submitForm(); return false;">
    <div class="page_block">
        ${csrf.token_input()}
        <label class="radio">
            <input type='radio' name='email-server' value='local'
                   onchange="localMailServerSelected()"
               %if not local.is_remote_host:
                   checked="checked"
               %endif
            >
            Use AeroFS Service Appliance's local mail relay
        </label>

        <label class="radio">
            <input type='radio' name='email-server' value='remote'
                   onchange="externalMailServerSelected()"
                %if local.is_remote_host:
                   checked="checked"
                %endif
            >
            Use external mail relay
        </label>

        ## The slide down options
        <div id="public-host-options"
            %if not local.is_remote_host:
                class="hide"
            %endif
        >

            <div class="row-fluid">
                <div class="span8">
                    <label for="email-sender-public-host">SMTP host:</label>
                    <input class="input-block-level" id="email-sender-public-host" name="email-sender-public-host" type="text"
                           ## We don't want to show "localhost" as the remote host
                           ## if the local mail relay is used.
                           value="${public_host_name if local.is_remote_host else ''}">
                </div>
                <div class="span4">
                    <%
                        # We don't want to show the local relay's port as the
                        # remote port if the local mail relay is used.
                        val = current_config['email.sender.public_port'] \
                            if local.is_remote_host else ''
                        if not val: val = '25'
                    %>
                    <label for="email-sender-public-port">SMTP port:</label>
                    <input class="input-block-level" id="email-sender-public-port" name="email-sender-public-port" type="text" value="${val}">
                </div>
            </div>

            <div class="row-fluid">
                <div class="span6">
                    <label for="email-sender-public-username">SMTP username:</label>
                    <input class="input-block-level" id="email-sender-public-username" name="email-sender-public-username" type="text" value="${current_config['email.sender.public_username']}">
                </div>
                <div class="span6">
                    <label for="email-sender-public-password">SMTP password:</label>
                    <input class="input-block-level" id="email-sender-public-password" name="email-sender-public-password" type="password" value="${current_config['email.sender.public_password']}">
                </div>
            </div>

            <label for="email-sender-public-enable-tls" class="checkbox">
                <input id="email-sender-public-enable-tls" name="email-sender-public-enable-tls" type="checkbox"
                    %if str2bool(current_config['email.sender.public_enable_tls']):
                        checked
                    %endif
                >Use STARTTLS encryption
            </label>
        </div>

        <p style="margin-top: 8px">AeroFS sends emails to users for various purposes such as sign up verification and folder invitations. A functional email server is required.</p>
    </div>

    <div class="page_block">
        <%
            val = current_config['base.www.support_email_address']
            if not val: val = default_support_email
        %>
        <h4>Support email address:</h4>
        <input class="input-block-level" id="base-www-support-email-address" name="base-www-support-email-address" type="text" value=${val}>
        <p>This email address is used for all "support" links. Set it to an email address
            you want users to send support requests to. It is also used as the
            "from" field for emails sent out by the system.</p>
    </div>
    <hr />
    ${common.render_previous_button()}
    ${common.render_next_button()}
</form>

<div id="verify-modal-email-input" class="modal hide small-modal" tabindex="-1" role="dialog">
    <div class="modal-header">
        <h4>Test your SMTP settings</h4>
    </div>

    <div class="modal-body">
        <p>Please test the email settings before proceeding. Enter your email
            address so that we can send you a test email.</p>
        <form id="verify-modal-email-input-form" method="post" class="form-inline"
                onsubmit="sendVerificationCodeAndShowCodeInputModal(); return false;">
            ${csrf.token_input()}

            <label for="verification-to-email">Email address:</label>
            <input id="verification-to-email" name="verification-to-email" type="text">
        </form>
    </div>
    <div class="modal-footer">
        <a href="#" class="btn" data-dismiss="modal">Close</a>
        <a href="#" id="send-verification-code-button" class="btn btn-primary"
           onclick="sendVerificationCodeAndShowCodeInputModal(); return false;">Send verification code</a>
    </div>
</div>

<div id="verify-modal-code-input" class="modal hide small-modal" tabindex="-1" role="dialog">
    <div class="modal-header">
        <h4>Enter test verification code</h4>
    </div>

    <div class="modal-body">
        <p>Please enter the verification code you received in the test email.</p>
        <form id="verify-modal-code-iput-form" method="post" class="form-inline"
                onsubmit="checkVerificationCodeAndSetConfiguration(); return false;">
            <label for="verification-code">Verification code:</label>
            <input id="verification-code" name="verification-code" type="text">
        </form>
    </div>
    <div class="modal-footer">
        <a href="#" class="btn" data-dismiss="modal">Close</a>
        <a href="#" id="continue-button" class="btn btn-primary"
            onclick="checkVerificationCodeAndSetConfiguration(); return false;">
            Verify</a>
    </div>
</div>

<div id="verify-succeed-modal" class="modal hide small-modal" tabindex="-1" role="dialog">
    <div class="modal-header">
        <h4 class="text-success">Test succeeded</h4>
    </div>

    <div class="modal-body">
        <p>Great! Your email server works. Let's move on to the next page.</p>
    </div>
    <div class="modal-footer">
        <a href="#" class="btn btn-primary"
            onclick="gotoNextPage(); return false;">
            Continue</a>
    </div>
</div>

<%def name="scripts()">
    <script>
        $(document).ready(function() {
            $('#verify-modal-email-input').on('shown', function() {
                $('#verification-to-email').focus();
                setEnabled($('#send-verification-code-button'), true);
            });

            $('#verify-modal-code-input').on('shown', function() {
                $('#verification-code').focus();
                setEnabled($('#continue-button'), true);
            });
        });

        function localMailServerSelected() {
            $('#public-host-options').hide();
        }

        function externalMailServerSelected() {
            $('#public-host-options').show();
        }

        var serializedData;
        function submitForm() {
            serializedData = $('form').serialize();

            if (!verifyPresence("base-www-support-email-address",
                        "Please specify a support email address.")) return;

            var remote = $(":input[name=email-server]:checked").val() == 'remote';
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

            var current_host = "${current_config['email.sender.public_host']}";
            var current_port = "${current_config['email.sender.public_port']}";
            var current_username = "${current_config['email.sender.public_username']}";
            var current_password = "${current_config['email.sender.public_password']}";
            var current_enable_tls = ${str(str2bool(current_config['email.sender.public_enable_tls'])).lower()};

            ## Only enable smtp verification modal if something has changed.
            var initial = ${str(not is_configuration_initialized).lower()};
            var toggled = remote != ${str(local.is_remote_host).lower()};
            if (initial || toggled || (remote &&
                    (host != current_host ||
                    port != current_port ||
                    username != current_username ||
                    password != current_password ||
                    enable_tls != current_enable_tls))) {
                ## As we are showing modals, do not disable nav buttons
                showVerifyEmailInputModal();

            } else {
                disableNavButtons();
                var support_email = $("#base-www-support-email-address").val();
                var current_support_email = "${current_config['base.www.support_email_address']}";

                if (support_email != current_support_email) {
                    doPost("${request.route_path('json_setup_email')}",
                        serializedData, gotoNextPage, enableNavButtons);
                } else {
                    gotoNextPage();
                }
            }
        }

        function sendVerificationCodeAndShowCodeInputModal() {
            if (!verifyPresence("verification-to-email",
                        "Please specify an email address.")) return;

            serializedData = serializedData + "&" + $('#verify-modal-email-input-form').serialize();

            var $btn = $('#send-verification-code-button');
            setEnabled($btn, false);

            doPost("${request.route_path('json_verify_smtp')}", serializedData,
                    showVerifyCodeInputModal, function() {
                        setEnabled($btn, true);
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
            var actualCode = parseInt("${email_verification_code}");

            if (inputtedCode == actualCode) {
                ## The button will be enabled next time the dialog shows.
                ## (The dialog is always dismissed after the doPost() call.)
                setEnabled($('#continue-button'), false);
                doPost("${request.route_path('json_setup_email')}",
                    serializedData, showVerificationSuccessModal, hideAllModals);
            } else {
                showErrorMessage("The verification code you provided was not correct.");
            }
        }

        function showVerificationSuccessModal() {
            hideAllModals();
            $('#verify-succeed-modal').modal('show');
        }
    </script>
</%def>