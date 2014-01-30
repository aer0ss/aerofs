<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="modal" file="../modal.mako"/>
<%namespace name="common" file="setup_common.mako"/>

<form method="POST" onsubmit="submitForm(); return false;">
    ${csrf.token_input()}

    <h4>This appliance's hostname:</h4>
    ## current_config is a template parameter
    <input class="input-block-level" id="base-host-unified" name="base.host.unified" type="text"/>

    <p>We recommend using <strong>share.*</strong> as the
        hostname. For example, ACME Inc. may choose <strong>share.acme.com</strong>.
        You need to configure your DNS server to point the hostname to this appliance's IP
        address.</p>
    <p>Changing the hostname in the future might ${require_reinstall()}</p>
    <hr />
    ${common.render_next_button()}
    ${common.render_previous_button()}
</form>

<%modal:modal>
    <%def name="id()">confirm-ip-address-modal</%def>
    <%def name="title()">Using IP addresses</%def>

    <p><strong>Not recommended</strong>:
        Using an IP address as the hostname is not recommended for production use,
        since changing it will ${require_reinstall()}</p>

    <p><strong>Requires fixed IP</strong>:
        Before proceeding with the IP address, please choose "Use Static IP" in the
        appliance console, or configure your DHCP server to assign a fixed IP for
        this appliance.</p>

    <%def name="footer()">
        <a href="#" class="btn" data-dismiss="modal">Cancel</a>
        <a href="#" class="btn btn-primary"
            onclick="askHostnameChangeConfirmationOrSubmit(); return false;">
            This appliance has a fixed IP. Proceed</a>
    </%def>
</%modal:modal>

<%modal:modal>
    <%def name="id()">confirm-change-modal</%def>
    <%def name="title()">Changing hostname</%def>

    Are you sure you want to change the hostname? It might ${require_reinstall()}

    <%def name="footer()">
        <a href="#" class="btn"
           onclick="restoreHostname(); return false;">Undo Change</a>
        <a href="#" class="btn btn-danger"
           onclick="confirmHostnameChange(); return false;">Proceed</a>
    </%def>
</%modal:modal>

<%def name="require_reinstall()">
    require users to reinstall AeroFS desktop apps and logout of mobile apps.
    <a href="https://support.aerofs.com/entries/22711364" target="_blank">Read more</a>.
</%def>

<%def name="scripts()">
    <script src="${request.static_path('web:static/js/purl.js')}"></script>
    <script>
        $(document).ready(function() {
            disableEsapingFromModal($('div.modal'));
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
                ## Warn about using the IP address if the hostname is changed and
                ## the new value is an IP address.
                if (val != "${current_config['base.host.unified']}" &&
                        isIPv4Address(val)) {
                    $('#confirm-ip-address-modal').modal('show');
                } else {
                    askHostnameChangeConfirmationOrSubmit();
                }
            }
        }

        ## Show a confirmation dialog if the hostname is changed from the
        ## previous value; otherwise submit the form.
        function askHostnameChangeConfirmationOrSubmit() {
            ## Hide the confirm-ip-address-modal
            hideAllModals();
            var val = $('#base-host-unified').val();
            ## Show a warning if the user changes the hostname.
            if (${1 if is_configuration_initialized else 0} &&
                    val != "${current_config['base.host.unified']}") {
                $('#confirm-change-modal').modal('show');
            } else {
                disableNavButtons();
                doSubmit();
            }
        }

        function confirmHostnameChange() {
            ## Hide the confirm-change-modal
            hideAllModals();
            doSubmit();
        }

        function restoreHostname() {
            ## Hide the confirm-change-modal
            hideAllModals();
            enableNavButtons();
            $('#base-host-unified').val("${current_config['base.host.unified']}");
        }

        function doSubmit() {
            var serializedData = $('form').serialize();
            doPost("${request.route_path('json_setup_hostname')}",
                    serializedData, gotoNextPage, enableNavButtons);
        }

        function isIPv4Address(hostname) {
            return /^(\d|[1-9]\d|1\d\d|2([0-4]\d|5[0-5]))\.(\d|[1-9]\d|1\d\d|2([0-4]\d|5[0-5]))\.(\d|[1-9]\d|1\d\d|2([0-4]\d|5[0-5]))\.(\d|[1-9]\d|1\d\d|2([0-4]\d|5[0-5]))$/.test(hostname);
        }
    </script>
</%def>
