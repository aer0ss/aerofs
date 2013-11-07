<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

<%
    public_host_name = current_config['email.sender.public_host']
    is_remote_host = public_host_name != "" and public_host_name != "localhost"
%>

<h4>Email server:</h4>

<form id="emailForm" method="POST">
    <div class="page_block">
        ${csrf.token_input()}
        <label class="radio">
            <input type='radio' name='email-server' value='local'
                   onchange="localMailServerSelected()"
               %if not is_remote_host:
                   checked="checked"
               %endif
            >
            Use AeroFS Service Appliance's local mail relay
        </label>

        <label class="radio">
            <input type='radio' name='email-server' value='remote'
                   onchange="externalMailServerSelected()"
                %if is_remote_host:
                   checked="checked"
                %endif
            >
            Use external mail relay
        </label>

        ## The slide down options
        <div id="public-host-options"
            %if not is_remote_host:
                class="hide"
            %endif
        >

            <div class="row-fluid">
                <div class="span8">
                    <label for="email-sender-public-host">SMTP host:</label>
                    <input class="input-block-level" id="email-sender-public-host" name="email-sender-public-host" type="text"
                           ## We don't want to show "localhost" as the remote host
                           ## if the local mail relay is used.
                           value="${public_host_name if is_remote_host else ''}">
                </div>
                <div class="span4">
                    <%
                        # We don't want to show the local relay's port as the
                        # remote port if the local mail relay is used.
                        val = current_config['email.sender.public_port'] \
                            if is_remote_host else ''
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
                <input id="email-sender-public-enable-tls" name="email-sender-public-enable-tls" type="checkbox" checked>Use STARTTLS encryption
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
    ${common.render_previous_button(page)}
    ${common.render_next_button("submitEmailForm()")}
</form>

<div id="verify-modal-email-input" class="modal hide small-modal" tabindex="-1" role="dialog">
    <div class="modal-header">
        <h4>Test your SMTP settings</h4>
    </div>

    <div class="modal-body">
        <p>Please test the email settings before proceeding. Enter your email
            address so that we can send you a test email.</p>
        <form id="verify-modal-email-input-form" method="post" class="form-inline"
                onsubmit="sendVerificationCodeAndEnableCodeModal(); return false;">
            ${csrf.token_input()}

            <label for="verification-to-email">Email address:</label>
            <input id="verification-to-email" name="verification-to-email" type="text">
        </form>
    </div>
    <div class="modal-footer">
        <a href="#" class="btn" data-dismiss="modal">Close</a>
        <a href="#" id="send-verification-code-button" class="btn btn-primary"
           onclick="sendVerificationCodeAndEnableCodeModal(); return false;">Send verification code</a>
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

<script type="text/javascript">
    function localMailServerSelected() {
        $('#public-host-options').hide();
    }

    function externalMailServerSelected() {
        $('#public-host-options').show();
    }

    function hideAllModalsAndEnableButtons() {
        $('div.modal').modal('hide');

        enableNavButtons();
        setEnabled($('#send-verification-code-button'), true);
        setEnabled($('#continue-button'), true);
    }

    function disableModalButtons() {
       setEnabled($('#send-verification-code-button'), false);
       setEnabled($('#continue-button'), false);
    }

    var serializedData;
    function submitEmailForm() {
        disableNavButtons();
        serializedData = $('#emailForm').serialize();

        if (!verifyPresence("base-www-support-email-address",
                    "Please specify a support email address.")) return;

        var remote = $(":input[name=email-server]:checked", '#emailForm').val() == 'remote';
        if (remote && (
                !verifyPresence("email-sender-public-host", "Please specify SMTP host.") ||
                !verifyPresence("email-sender-public-port", "Please specify SMTP port."))) {
            return;
        }

        var host = $("#email-sender-public-host").val();
        var port = $("#email-sender-public-port").val();
        var username = $("#email-sender-public-username").val();
        var password = $("#email-sender-public-password").val();
        var enable_tls = $("#email-sender-public-enable-tls").val();

        var current_host = "${current_config['email.sender.public_host']}";
        var current_port = "${current_config['email.sender.public_port']}";
        var current_username = "${current_config['email.sender.public_username']}";
        var current_password = "${current_config['email.sender.public_password']}";
        var current_enable_tls = "${current_config['email.sender.public_enable_tls']}";

        ## Only enable smtp verification modal if something has changed.
        var initial = ${str(not is_configuration_initialized).lower()};
        var toggled = remote != ${str(is_remote_host).lower()};
        if (initial || toggled || (remote &&
                (host != current_host ||
                port != current_port ||
                username != current_username ||
                password != current_password ||
                enable_tls != current_enable_tls))) {
            enableVerifyModalEmailInput();
        } else {
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

    function sendVerificationCodeAndEnableCodeModal() {
        if (!verifyPresence("verification-to-email",
                    "Please specify an email address.")) return;

        serializedData = serializedData + "&" + $('#verify-modal-email-input-form').serialize();

        disableModalButtons();
        doPost("${request.route_path('json_verify_smtp')}",
            serializedData, enableVerifyModalCodeInput, hideAllModalsAndEnableButtons);
    }

    function enableVerifyModalEmailInput() {
        hideAllModals();
        $('#verify-modal-email-input').modal('show');
        $('#verification-to-email').focus();
    }

    function enableVerifyModalCodeInput() {
        hideAllModals();
        $('#verify-modal-code-input').modal('show');
        $('#verification-code').focus();
    }

    function checkVerificationCodeAndSetConfiguration() {
        var inputtedCode = $("#verification-code").val();
        var actualCode = parseInt("${email_verification_code}");

        if (inputtedCode == actualCode) {
            doPost("${request.route_path('json_setup_email')}",
                serializedData, showVerificationSuccessModal, hideAllModalsAndEnableButtons);
        } else {
            displayError("The verification code you provided was not correct.");
        }
    }

    function showVerificationSuccessModal() {
        hideAllModals();
        $('#verify-succeed-modal').modal('show');
    }
</script>
