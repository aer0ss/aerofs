<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

<h5>Email</h5>

<p>AeroFS sends emails to users for many different purposes. On this page you can configure your support email address and SMTP credentials. If you do not specify SMTP information, AeroFS will use its own mail setup.</p>

<hr/>
<form id="emailForm">
    ${csrf.token_input()}
    <table width="100%">
        <tr>
        <td width="30%"><label for="base.www.support_email_address">Support Email:</label></td>

        <td width="70%"><input class="span6" id="base.www.support_email_address" name="base.www.support_email_address" type="text" value=${current_config['base.www.support_email_address']}></td>
        </tr>

        <tr>
        <td width="30%"><label for="email.sender.public_host">SMTP Host:</label></td>
        <td width="70%"><input class="span6" id="email.sender.public_host" name="email.sender.public_host" type="text" value=${current_config['email.sender.public_host']}></td>
        </tr>

        <tr>
        <td width="30%"><label for="email.sender.public_username">SMTP Username:</label></td>
        <td width="70%"><input class="span6" id="email.sender.public_username" name="email.sender.public_username" type="text" value=${current_config['email.sender.public_username']}></td>
        </tr>

        <tr>
        <td width="30%"><label for="email.sender.public_password">SMTP Password:</label></td>
        <td width="70%"><input class="span6" id="email.sender.public_password" name="email.sender.public_password" type="password" value=${current_config['email.sender.public_password']}></td>
        </tr>
    </table>
    <hr/>

    <table width="100%" align="left|right">
    <tr>
    <td>${common.render_previous_button(page)}</td>
    <td align="right">${common.render_next_button("submitEmailForm()")}</td>
    </tr>
    </table>
</form>

<script type="text/javascript">
    function submitEmailForm() {
        disableButtons();

        if (verifyPresence("base.www.support_email_address", "Must specify a support email address."))
        {
            var $form = $('#emailForm');
            var serializedData = $form.serialize();

            doPost("${request.route_path('json_config_email')}",
                serializedData, gotoNextPage);
        }

        event.preventDefault();
    }
</script>
