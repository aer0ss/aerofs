<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

<% public_host = current_config['email.sender.public_host'] %>

<h4>Email server:</h4>

<form id="emailForm" method="POST">
    <div class="page_block">
        ${csrf.token_input()}
        <label class="radio">
            <input type='radio' id='local-mail-server' name='email.server' value='local'
                   onchange="localMailServerSelected()"
               %if not public_host:
                   checked
               %endif
            >
            Use AeroFS Service Appliance's local mail relay
        </label>

        <label class="radio">
            <input type='radio' name='email.server' value='remote'
                   onchange="externalMailServerSelected()"
                %if public_host:
                   checked
                %endif
            >
            Use external mail relay
        </label>

        <label for="email.sender.public_host">SMTP host:</label>
        <input class="input-block-level public-host-option" id="email.sender.public_host" name="email.sender.public_host" type="text" value="${public_host}"
            %if not public_host:
                disabled
            %endif
        >

        <div class="row-fluid">
            <div class="span6">
                <label for="email.sender.public_username">SMTP username:</label>
                <input class="input-block-level public-host-option" id="email.sender.public_username" name="email.sender.public_username" type="text" value="${current_config['email.sender.public_username']}"
                    %if not public_host:
                        disabled
                    %endif
                >
            </div>
            <div class="span6">
                <label for="email.sender.public_password">SMTP password:</label>
                <input class="input-block-level public-host-option" id="email.sender.public_password" name="email.sender.public_password" type="password" value="${current_config['email.sender.public_password']}"
                    %if not public_host:
                        disabled
                    %endif
                >
            </div>
        </div>

        <label for="foo" class="checkbox">
            <input name="foo" type="checkbox" checked disabled>Enable TLS encryption <small>(plaintext STMP not supported at this moment)</small>
        </label>

        <p style="margin-top: 8px">AeroFS sends emails to users for various purposes: sign up verification, folder invitations, and so on. A functional email server is required.</p>
    </div>

    <div class="page_block">
        <h4>Support email address:</h4>
        <input class="input-block-level" id="base.www.support_email_address" name="base.www.support_email_address" type="text" value=${current_config['base.www.support_email_address']}>
        <p>This email address is used for all "support" links. Set it to an email address you want users to send support requests to. The default value is <code>support@aerofs.com</code>.</p>
    </div>
    <hr />
    ${common.render_previous_button(page)}
    ${common.render_next_button("submitEmailForm()")}
</form>

<div id="verify-modal-email-input" class="modal hide small-modal" tabindex="-1" role="dialog">
    <div class="modal-header">
        <h4>Verify Your SMTP Settings</h4>
    </div>

    <div class="modal-body">
        <p>You must verify your email settings before you can continue. Enter your email address so that we can send a verification email.</p>
        <form id="verify-modal-email-input-form" method="post" class="form-inline"
                onsubmit="sendVerificationCodeAndEnableCodeModal(); return false;">
            <label for="verification.to.email">Email address:</label>
            <input id="verification.to.email" name="verification.to.email" type="text">
        </form>
    </div>
    <div class="modal-footer">
        <a href="#" id="sendVerificationCodeButton" class="btn btn-primary"
           onclick="sendVerificationCodeAndEnableCodeModal(); return false;">Send verification code</a>
    </div>
</div>

<div id="verify-modal-code-input" class="modal hide small-modal" tabindex="-1" role="dialog">
    <div class="modal-header">
        <h4>Verify Your SMTP Settings</h4>
    </div>

    <div class="modal-body">
        <p>Enter the verification code you received in your email to continue.</p>
        <form id="verify-modal-code-iput-form" method="post" class="form-inline"
                onsubmit="checkVerificationCodeAndSetConfiguration();">
            <label for="verification.code">Verification code:</label>
            <input id="verification.code" name="verification.code" type="text">
        </form>
    </div>
    <div class="modal-footer">
        <a href="#" id="continueButton" class="btn btn-primary"
           onclick="checkVerificationCodeAndSetConfiguration();">Continue</a>
    </div>
</div>

<script type="text/javascript">
    function localMailServerSelected() {
        $('.public-host-option').attr("disabled", "disabled");
    }

    function externalMailServerSelected() {
        $('.public-host-option').removeAttr("disabled");
    }

    function hideAllModals() {
        $('div.modal').modal('hide');
        enableButtons();

        setEnabled($('#sendVerificationCodeButton'), true);
        setEnabled($('#continueButton'), true);
    }

    function disableModalButtons() {
       setEnabled($('#sendVerificationCodeButton'), false);
       setEnabled($('#continueButton'), false);
    }

    function submitEmailForm() {
        disableButtons();

        if (!verifyPresence("base.www.support_email_address",
                    "Please specify a support email address.")) return false;

        var remote = $("input[name=email.server]:checked", '#emailForm').val() == 'remote';
        if (remote && (
                !verifyPresence("email.sender.public_host", "Please specify SMTP host.") ||
                !verifyPresence("email.sender.public_username", "Please specify SMTP username.") ||
                !verifyPresence("email.sender.public_password", "Please specify SMTP password."))) {
            return false;
        }

        var host = document.getElementById("email.sender.public_host").value;
        var username = document.getElementById("email.sender.public_username").value;
        var password = document.getElementById("email.sender.public_password").value;

        var current_host = "${current_config['email.sender.public_host']}";
        var current_username = "${current_config['email.sender.public_username']}";
        var current_password = "${current_config['email.sender.public_password']}";

        ## Only enable smtp verification modal if something has changed.
        if ((remote &&
                (host != current_host ||
                username != current_username ||
                password != current_password)) ||
            (!remote &&
                (current_host != "localhost" ||
                current_username != "" ||
                current_password != ""))) {
            enableVerifyModalEmailInput();
        } else {
            gotoNextPage();
        }

        return false;
    }

    var serializedData;
    function sendVerificationCodeAndEnableCodeModal() {
        if (!verifyPresence("verification.to.email",
                    "Please specify an email address.")) return;

        var verificationEmail = document.getElementById("verification.to.email").value;
        serializedData = $('#emailForm').serialize() + "&verification.to.email=" + verificationEmail;

        disableModalButtons();
        doPost("${request.route_path('json_verify_smtp')}",
            serializedData, enableVerifyModalCodeInput, hideAllModals);
    }

    function enableVerifyModalEmailInput() {
        hideAllModals();
        $('#verify-modal-email-input').modal('show');
        return false;
    }

    function enableVerifyModalCodeInput() {
        hideAllModals();
        $('#verify-modal-code-input').modal('show');
        return false;
    }

    function checkVerificationCodeAndSetConfiguration() {
        var inputtedCode = document.getElementById("verification.code").value;
        var actualCode = parseInt("${email_verification_code}");

        if (inputtedCode == actualCode) {
            doPost("${request.route_path('json_setup_email')}",
                serializedData, gotoNextPage, hideAllModals);
        } else {
            displayError("The verification code you provided was not correct.");
        }
    }
</script>
