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

        <p>AeroFS sends emails to users for various purposes: sign up verification, folder invitations, and so on. A functional email server is required.</p>
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

<script type="text/javascript">
    function localMailServerSelected() {
        $('.public-host-option').attr("disabled", "disabled");
    }

    function externalMailServerSelected() {
        $('.public-host-option').removeAttr("disabled");
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

        var serializedData = $('#emailForm').serialize();

        doPost("${request.route_path('json_setup_email')}",
            serializedData, gotoNextPage, enableButtons);
        return false;
    }
</script>
