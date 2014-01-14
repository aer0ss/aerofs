<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="modal" file="../modal.mako"/>
<%namespace name="common" file="setup_common.mako"/>

<form method="POST" onsubmit="submitForm(); return false;">
    ${csrf.token_input()}

    <h4>Hostname:</h4>
    <%
        val = current_config['base.host.unified']
        if not val: val = 'share.'
    %>
    ## current_config is a template parameter
    <input class="input-block-level" id="base-host-unified" name="base.host.unified" type="text" value="${val}" />

    <p>This is your AeroFS Appliance's hostname. We recommend using <code>share.*</code> as the
        hostname. For example, ACME Corporation may choose <code>share.acme.com</code>.</p>
    <p>You need to configure the hostname's DNS entry to point to the IP assigned to the appliance.
        If you're using VirtualBox, get the the IP from the appliance's console. If you're using
        OpenStack, configure a floating IP for this instance.</p>
    <hr />
    ${common.render_next_button()}
    ${common.render_previous_button()}
</form>

<%modal:modal>
    <%def name="id()">confirm-modal</%def>
    <%def name="title()">Changing hostname</%def>
    <%def name="footer()">
        <a href="#" class="btn"
           onclick="restoreHostname(); return false;">Undo Change</a>
        <a href="#" class="btn btn-danger"
           onclick="confirmHostnameChange(); return false;">Proceed</a>
    </%def>
    Are you sure you want to change the hostname? Depending on your DNS setup,
    it might require users to reinstall AeroFS desktop apps and logout of mobile apps.
    <a href="https://support.aerofs.com/entries/22711364" target="_blank">Read more</a>.
</%modal:modal>

<%def name="scripts()">
    <script>
        $(document).ready(function() {
            $('#base-host-unified').focus();
            disableEsapingFromModal($('div.modal'));
        });

        function submitForm() {
            disableNavButtons();

            if (verifyPresence("base-host-unified", "Please specify a hostname.")) {
                var val = $('#base-host-unified').val();
                if (${1 if is_configuration_initialized else 0} &&
                        val != "${current_config['base.host.unified']}") {
                    $('#confirm-modal').modal('show');
                } else {
                    doSubmit();
                }
            }
        }

        function restoreHostname() {
            hideAllModals();
            enableNavButtons();
            $('#base-host-unified').val("${current_config['base.host.unified']}");
        }

        function confirmHostnameChange() {
            hideAllModals();
            doSubmit();
        }

        function doSubmit() {
            var serializedData = $('form').serialize();
            doPost("${request.route_path('json_setup_hostname')}",
                    serializedData, gotoNextPage, enableNavButtons);
        }
    </script>
</%def>
