<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Sync Settings" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="loader" file="loader.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>
<%namespace name="modal" file="modal.mako"/>

<h2>Sync Settings</h2>

<h4>Sync Mode</h4>

<p class="page-block">For improved auditing, disable LAN sync and use only
relay sync. The default sync setting is to use both LAN and relay sync between
clients.</p>

<div class="page-block">
    ${sync_options_form()}
</div>

<%def name="sync_options_form()">
    <form method="POST" onsubmit="warning(); return false;">
        ${csrf.token_input()}
        <label class="radio">
            <input type="radio" name="enable-lansync" id="lansync-disabled" value="false" onchange="relaySyncSelected()"
                %if not is_lansync_enabled:
                    checked
                %endif
            >
            Use only relay sync
        </label>

        <label class="radio">
            <input type="radio" name="enable-lansync" id="lansync-enabled" value="true" onchange="lanSyncSelected()"
                %if is_lansync_enabled:
                    checked
                %endif
            >
            Use both LAN and relay sync
        </label>

        <div id="port-range-options"
             %if not is_lansync_enabled:
                style="display: none;"
             %endif
        >
            ${port_range_options()}
        </div>


    <%def name="port_range_options()">
        <br/>
        <h4>Device Port Range</h4>

        <p>Specify a range of inbound ports for connections between desktop clients to facilitate LAN sync.</p>

        </br>
        <label class="radio">
            <input type="radio" name="enable-custom-ports" id="default-ports-enabled" value="false" onchange="defaultPortsSelected()"
                   %if not is_custom_ports_enabled:
                   checked
                   %endif
                    >
            Use default ports
        </label>

        <label class="radio">
            <input type="radio" name="enable-custom-ports" id="custom-ports-enabled" value="true" onchange="customPortsSelected()"
                   %if is_custom_ports_enabled:
                   checked
                   %endif
                    >
            Use custom ports
        </label>

        <div id="custom-port-range-options"
             %if not is_custom_ports_enabled:
             style="display: none;"
             %endif
                >
            ${custom_port_range_options()}
        </div>
    </%def>

    <%def name="custom_port_range_options()">
        <br/>
        <div class="row">
            <div class="col-sm-3">
                <label for="port-range-low">Port Range Lower Bound:</label>
                <input class="form-control" id="port-range-low" name="port-range-low" type="text"
                    value="${device_port_range_low}" />
            </div>
            <div class="col-sm-3">
                <label for="port-range-high">Port Range Upper Bound:</label>
                <input class="form-control" id="port-range-high" name="port-range-high" type="text"
                       value="${device_port_range_high}" />
            </div>
        </div>

        <br/>

    </%def>

        <br/>
        <div class="row">
            <div class="col-sm-6">
                <button id="save-btn" class="btn btn-primary">Save</button>
            </div>
        </div>

    </form>
</%def>

<%progress_modal:progress_modal>
    <%def name="id()">sync-modal</%def>
    Configuring your sync settings...
</%progress_modal:progress_modal>

<%modal:modal>
    <%def name="id()">disable-lan-sync-modal</%def>
    <%def name="title()">Disabling LAN sync...</%def>
    <%def name="error()"></%def>

    <p><strong>Is not recommended</strong>:
        Disabling LAN sync drastically increases load on your appliance and decreases network performance. </p>

    <%def name="footer()">
        <a href="#" class="btn btn-default" data-dismiss="modal">Cancel</a>
        <a href="#" class="btn btn-primary"
            onclick="submitForm(); return false;">
            Disable LAN sync. Proceed.</a>
    </%def>
</%modal:modal>

<%block name="scripts">
    <%loader:scripts/>
    <%spinner:scripts/>
    <%progress_modal:scripts/>
    <script>
        $(document).ready(function() {
            initializeProgressModal();
        });

        %if is_lansync_enabled:
            lanSyncSelected();
        %else:
            relaySyncSelected();
        %endif

        %if is_custom_ports_enabled:
            customPortsSelected();
        %else:
            defaultPortsSelected();
        %endif

        $('#sync-modal').modal({
            backdrop: 'static',
            keyboard: false,
            show: false
        });

        function submitForm() {
            var $progress = $('#sync-modal');
            $progress.modal('show');

            $.post("${request.route_path('json_set_sync_settings')}",
                    $('form').serialize())
            .done(restartServices)
            .fail(function(xhr) {
                showErrorMessageFromResponse(xhr);
                $progress.modal('hide');
            });
        }

        function restartServices() {
            var $progress = $('#sync-modal');
            reboot('current', function() {
                $progress.modal('hide');
                showSuccessMessage('New configuration is saved. Your desktop clients will need to be restarted for this change to take effect.');
            }, function(xhr) {
                $progress.modal('hide');
                showErrorMessageFromResponse(xhr);
            });
        }

        function warning() {
            if (document.getElementById('lansync-disabled').checked){
                $('#disable-lan-sync-modal').modal('show');
            }
            else{
                submitForm();
            }
        }

        function hideWarning() {
            hideAllMessages();
        }

        function lanSyncSelected() {
            $('#port-range-options').show();
        }

        function relaySyncSelected() {
            $('#port-range-options').hide();
        }

        function customPortsSelected() {
            $('#custom-port-range-options').show();
        }

        function defaultPortsSelected() {
            $('#custom-port-range-options').hide();
        }
    </script>
</%block>
