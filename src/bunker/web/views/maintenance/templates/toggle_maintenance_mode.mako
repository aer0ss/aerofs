<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Backup" %>

<%namespace name="progress_modal" file="progress_modal.mako"/>
<%namespace name="common" file="backup_common.mako"/>
<%namespace name="bootstrap" file="bootstrap.mako"/>
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
    <a href="${request.route_path('backup_appliance')}">backup</a> and
    <a href="${request.route_path('upgrade_appliance')}">upgrade</a>.</p>

<hr/>

<p>
    <button class="btn btn-primary"
            onclick="enterOrExitMaintenance(); return false;">
        %if is_maintenance_mode:
            Exit
        %else:
            Enter
        %endif
        Maintenance Mode
    </button>
</p>

<%progress_modal:html>
    %if is_maintenance_mode:
        Exiting
    %else:
        Entering
    %endif
    maintenance mode...
</%progress_modal:html>

<%block name="scripts">
    <%progress_modal:scripts/>
    ## spinner support is required by progress_modal
    <%spinner:scripts/>
    <%bootstrap:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
        });

        function enterOrExitMaintenance() {
            $('#${progress_modal.id()}').modal('show');

            var task =
                %if is_maintenance_mode:
                    'maintenance-exit';
                %else:
                    'maintenance-enter';
                %endif

            runBootstrapTask(task, function() {
                ## reload the page to refresh the value of is_maintenance_mode.
                location.reload();
            }, function() {
                $('#${progress_modal.id()}').modal('hide');
            });
        }
    </script>
</%block>
