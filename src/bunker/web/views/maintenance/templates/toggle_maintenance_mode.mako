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
    <code>${request.route_url('maintenance_home')}</code>
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

        function enterOrExitMaintenance() {
            $('#toggle-modal').modal('show');

            var target =
                %if is_maintenance_mode:
                    'default';
                %else:
                    'maintenance';
                %endif

            reboot(target, function() {
                ## reload the page to refresh the value of is_maintenance_mode.
                location.reload();
            }, function(xhr) {
                $('#toggle-modal').modal('hide');
                showErrorMessageFromResponse(xhr);
            });
        }
    </script>
</%block>
