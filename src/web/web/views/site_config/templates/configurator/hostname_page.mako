<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

<h4>DNS and Hostnames</h4>

<p>You need to configure DNS in your office to point to the static IP held by this system. If you're using openstack, configure a floating IP for this instance. If you're using VirtualBox, get your static IP from your system console.</p>
<p>We recommend the following hostname convention:</p>
<p class="text-center"><b>share.&lt;company&gt;.&lt;domain&gt;</b></p>
<p>For example, at the AeroFS office we might choose share.aerofs.com. Once you have your DNS set up, enter the hostname you used press "Next".</p>

<hr/>
<form id="hostnameForm" method="POST">
    ${csrf.token_input()}
    <table width="100%">
        <tr>
        <td width="30%"><label for="base.host.unified">System Hostname:</label></td>
        ## current_config is a template parameter
        <td width="70%"><input class="span6" id="base.host.unified" name="base.host.unified" type="text" value=${current_config['base.host.unified']}></td>
        </tr>
    </table>
    <hr/>

    ${common.render_previous_button(page)}
    ${common.render_next_button("submitHostnameForm()")}
</form>

<script type="text/javascript">
    function submitHostnameForm()
    {
        disableButtons();

        if (verifyPresence("base.host.unified", "Must specify a system hostname."))
        {
            var $form = $('#hostnameForm');
            var serializedData = $form.serialize();

            doPost("${request.route_path('json_config_hostname')}",
                serializedData, gotoNextPage);
        }

        event.preventDefault();
    }
</script>
