<%inherit file="maintenance_layout.mako"/>
<%! page_title = "LAN Sync Setting" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="loader" file="loader.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>
<%namespace name="modal" file="modal.mako"/>

<h2>Sync Settings</h2>

<p class="page-block">For improved auditability services, disable LAN sync and
use only relay sync. The default sync setting is to use both LAN and relay sync
between clients.</p>

<div class="page-block">
    ${sync_options_form()}
</div>

<%def name="sync_options_form()">
    <form method="POST" onsubmit="warning(); return false;">
        ${csrf.token_input()}
        <label class="radio">
            <input type="radio" name="enable-lansync" id="lansync-disabled" value="false"
                %if not is_lansync_enabled:
                    checked
                %endif
            >
            Relay sync
        </label>

        <label class="radio">
            <input type="radio" name="enable-lansync" value="true"
                %if is_lansync_enabled:
                    checked
                %endif
            >
            LAN and relay sync
        </label>

        <div class="row">
            <div class="col-sm-6">
                <button id="save-btn" class="btn btn-primary">Save</button>
            </div>
        </div>
    </form>
</%def>

<%progress_modal:html>
    Configuring your sync settings...
</%progress_modal:html>

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

        function submitForm() {
            var $progress = $('#${progress_modal.id()}');
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
            var $progress = $('#${progress_modal.id()}');
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

    </script>
</%block>
