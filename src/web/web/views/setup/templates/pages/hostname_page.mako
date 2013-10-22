<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

<form id="hostnameForm" method="POST">
    ${csrf.token_input()}

    <h4>Hostname:</h4>

    ## current_config is a template parameter
    <input class="input-block-level" id="base.host.unified" name="base.host.unified" type="text" value=${current_config['base.host.unified']}>

    <p>This is your AeroFS Appliance's hostname. You need to configure the hostname's DNS entry to point to the IP assigned to the appliance.</p>
    <p>If you're using VirtualBox, get the appliance's IP from its console. If you're using OpenStack, configure a floating IP for this instance.</p>
    <p>We recommend using <code>share.*</code> as the hostname. For example, ACME Corporation may choose <code>share.acme.com</code>.</p>
    <hr />
    ${common.render_next_button("submitHostnameForm()")}
</form>

<script type="text/javascript">
    function submitHostnameForm() {
        disableButtons();

        if (verifyPresence("base.host.unified", "Please specify a hostname.")) {
            var $form = $('#hostnameForm');
            var serializedData = $form.serialize();

            doPost("${request.route_path('json_setup_hostname')}",
                serializedData, gotoNextPage, enableButtons);
        }
    }
</script>
