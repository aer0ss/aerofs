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
    <p>Changing the hostname in the future ${might_require_reinstall()}</p>
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
    Are you sure you want to change the hostname? It ${might_require_reinstall()}
</%modal:modal>

<%def name="might_require_reinstall()">
    might require users to reinstall AeroFS desktop apps and logout of mobile apps.
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
