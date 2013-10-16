<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

<form id="hostnameForm" method="POST">
    ${csrf.token_input()}

    <label for="base.host.unified"><h4>Hostname:</h4></label>
    ## current_config is a template parameter
    <input class="input-block-level" id="base.host.unified" name="base.host.unified" type="text" value=${current_config['base.host.unified']}>

    <p>This is your AeroFS Service Appliance's hostname. You need to configure DNS in your office to point to the IP held by this system. If you're using VirtualBox, get your static IP from your system console. If you're using OpenStack, configure a floating IP for this instance.</p>
    <p>We recommend the following hostname convention:</p>
    <p class="text-center"><strong>share.</strong>&lt;company&gt;.&lt;domain&gt;</p>
    <p>For example, at the AeroFS office we might choose share.aerofs.com.</p>
    <p>Before proceeding, please ensure the DNS is properly set up.</p>

    <hr/>

    ${common.render_previous_button(page)}
    ${common.render_next_button("submitHostnameForm()")}
</form>

<script type="text/javascript">
    function submitHostnameForm()
    {
        disableButtons();

        if (verifyPresence("base.host.unified", "Please specify a hostname."))
        {
            var $form = $('#hostnameForm');
            var serializedData = $form.serialize();

            doPost("${request.route_path('json_setup_hostname')}",
                serializedData, gotoNextPage, enableButtons);
        }

        event.preventDefault();
    }
</script>
