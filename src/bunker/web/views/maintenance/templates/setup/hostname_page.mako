<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="modal" file="../modal.mako"/>
<%namespace name="common" file="setup_common.mako"/>

<form method="POST" role="form" onsubmit="submitForm(); return false;">
    ${csrf.token_input()}

    <h4>This appliance's hostname:</h4>
    ## current_config is a template parameter
    <div class="form-group">
        <input class="form-control" id="base-host-unified" name="base.host.unified" type="text"/>

        <p class="help-block">We recommend using <strong>share.*</strong> as the
            hostname. For example, ACME Inc. may choose <strong>share.acme.com</strong>.
            You need to configure your DNS server to point the hostname to this appliance's IP
            address.</p>
        <p class="help-block">Changing the hostname in the future might ${require_reinstall()}</p>
    </div>
    ${common.render_next_prev_buttons()}
</form>

<%modal:modal>
    <%def name="id()">confirm-ip-address-modal</%def>
    <%def name="title()">Using IP addresses...</%def>
    <%def name="error()"></%def>

    <p><strong>Is not recommended</strong>:
        Using an IP address as the hostname is not recommended for production use,
        since changing it will ${require_reinstall()}</p>

    <p><strong>Requires fixed IP</strong>:
        Before proceeding with the IP address, please choose "Use Static IP" in the
        appliance console, or configure your DHCP server to assign a fixed IP for
        this appliance.</p>

    <%def name="footer()">
        <a href="#" class="btn btn-default" data-dismiss="modal">Cancel</a>
        <a href="#" class="btn btn-primary"
            onclick="confirmHostnameChange(); return false;">
            This appliance has a fixed IP. Proceed</a>
    </%def>
</%modal:modal>

<%modal:modal>
    <%def name="id()">confirm-change-modal</%def>
    <%def name="title()">Changing hostname</%def>

    Are you sure you want to change the hostname? It might ${require_reinstall()}

    <%def name="footer()">
        <a href="#" class="btn btn-default"
           onclick="restoreHostname(); return false;">Undo Change</a>
        <a href="#" class="btn btn-danger"
           onclick="confirmFirewall(); return false;">Proceed</a>
    </%def>
</%modal:modal>

<%modal:modal>
    <%def name="id()">confirm-firewall-modal</%def>
    <%def name="title()">Firewall rules</%def>

    <p>The following ports need to be open for AeroFS desktop clients and Team
        Servers to connect to the appliance:</p>
    <ul>
        ## N.B. REMEMBER TO UPDATE https://support.aerofs.com/entries/22661589 as well
        <li>TCP ports: 80, 443, 3478, 4433, 5222, 8084, 8484, 8888, and 29438.</li>
        <li>UDP port: 3478.</li>
    </ul>

    <p>Your firewall or VPN may require configuration to unblock these ports for
        your AeroFS clients. Please check this now.</p>
    <p><a target="_blank"
        href="https://support.aerofs.com/entries/22661589">
        Read more about network requirements</a>.</p>

    <%def name="footer()">
        <a class="btn btn-default" href="#" data-dismiss="modal">Cancel</a>
        <a class="btn btn-primary"
            onclick="$('#confirm-firewall-modal').modal('hide'); doSubmit(); return false;">
            I've unblocked all the ports. Continue</a>
    </%def>
</%modal:modal>

<%def name="require_reinstall()">
    require users to reinstall AeroFS desktop apps and relink mobile apps.
    <a href="https://support.aerofs.com/entries/22711364" target="_blank">Read more</a>.
</%def>

<%def name="scripts()">
    <script src="${request.static_path('web:static/js/purl.js')}"></script>
    <script>
        $(document).ready(function() {
            disableEscapingFromModal($('div.modal'));
            populateAndFocusHostname();
        });

        function populateAndFocusHostname() {
            var current = "${current_config.get('base.host.unified', '')}";
            if (!current) {
                ## Use the current page's hostname
                current = $.url().attr('host');
            }

            ## Set the value, select the whole text, and place focus.
            $('#base-host-unified').val(current).select().focus();
        }

        function submitForm() {
            if (verifyPresence("base-host-unified", "Please specify a hostname.")) {

                var val = $('#base-host-unified').val();
                var changedOrInitialConfiguration = val != "${current_config['base.host.unified']}";
                if (changedOrInitialConfiguration) {
                    ## Show various warning dialogs
                    if (isIPv4Address(val)) confirmIPAddress();
                    else confirmHostnameChange();
                } else {
                    ## No change. No warning dialogs
                    doSubmit();
                }
            }
        }

        function confirmIPAddress() {
            $('#confirm-ip-address-modal').modal('show');
        }

        ## Show a confirmation dialog if the hostname is changed from the
        ## previous value; otherwise proceed to the next step
        function confirmHostnameChange() {
            var newVal = $('#base-host-unified').val();
            ## Show a warning if the user changes the hostname.
            if (${1 if is_configuration_initialized else 0} &&
                    newVal != "${current_config['base.host.unified']}") {
                $('#confirm-change-modal').modal('show');
            } else {
                confirmFirewall();
            }
        }

        function restoreHostname() {
            ## Hide the confirm-change-modal
            hideAllModals();
            $('#base-host-unified').val("${current_config['base.host.unified']}");
        }

        function confirmFirewall() {
            $('#confirm-firewall-modal').modal('show');
        }

        function doSubmit() {
            hideAllModals();
            disableNavButtons();
            doPost("${request.route_path('json_setup_hostname')}",
                    $('form').serialize(), gotoNextPage, enableNavButtons);
        }

        function isIPv4Address(hostname) {
            return /^(\d|[1-9]\d|1\d\d|2([0-4]\d|5[0-5]))\.(\d|[1-9]\d|1\d\d|2([0-4]\d|5[0-5]))\.(\d|[1-9]\d|1\d\d|2([0-4]\d|5[0-5]))\.(\d|[1-9]\d|1\d\d|2([0-4]\d|5[0-5]))$/.test(hostname);
        }
    </script>
</%def>
