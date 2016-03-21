<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Backup" %>

<%namespace name="progress_modal" file="progress_modal.mako"/>
<%namespace name="loader" file="loader.mako"/>
<%namespace name="spinner" file="spinner.mako"/>

<h2>
    %if is_maintenance_mode:
        Exit
    %else:
        Enter
    %endif
    maintenance mode
</h2>

<p>Disable access to your appliance to perform maintenance. Web pages will
    display appropriate maintenance messages when accessed. Appliance management
    pages will still be available at the following URL:
</p>
<p>
    <code>${base_url}/admin</code>
</p>

<p>The appliance enters maintenance mode automatically during
    <a href="${request.route_path('backup_and_upgrade')}">backup and upgrade</a>.

<hr/>

<p>
    <button class="btn btn-primary"
            onclick="enterOrExitMaintenance(); return false;">
        %if is_maintenance_mode:
            Exit
        %else:
            Enter
        %endif
        maintenance mode
    </button>
</p>

<%progress_modal:progress_modal>
    <%def name="id()">toggle-modal</%def>
    %if is_maintenance_mode:
        Exiting
    %else:
        Entering
    %endif
    maintenance mode...
</%progress_modal:progress_modal>

<%block name="scripts">
    <%progress_modal:scripts/>
    ## spinner support is required by progress_modal
    <%spinner:scripts/>
    <%loader:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
        });

        $('#toggle-modal').modal({
            backdrop: 'static',
            keyboard: false,
            show: false
        });

        ## reboot the appliance into the target mode and handle modals and error messaging
        function rebootAndRefresh(target) {
            reboot(target, function() {
                ## reload the page to refresh the value of is_maintenance_mode.
                location.reload();
            }, function(xhr) {
                $('#toggle-modal').modal('hide');
                showErrorMessageFromResponse(xhr);
            });
            return false;
        }

        ## Reboots the system into the target mode. Validates the current license file before
        ## booting into the default mode.
        function enterOrExitMaintenance() {
            $('#toggle-modal').modal('show');
            var target =
                %if is_maintenance_mode:
                    %if onboard_storage:
                        'onboard';
                    %else:
                        'default';
                %else:
                    'maintenance';
                %endif

            if (target == 'maintenance') {
                rebootAndRefresh(target);
            } else if (target == 'default') {
                ## validate the current license using the server, so we don't need to worry about
                ## timezones
                $.get("${request.route_path('validate_license')}", function(data) {
                    if (data['license_valid'] == true) {
                        rebootAndRefresh(target);
                    } else {
                        $('#toggle-modal').modal('hide');
                        showErrorMessage('Your license has expired. To exit maintenance mode, ' +
                            'first upload a new license file.');
                    }
                }, 'json').fail( function(xhr) {
                    $('#toggle-modal').modal('hide');
                    showErrorMessageFromResponse(xhr);
                });
            }
            return false;
        }
    </script>
</%block>
