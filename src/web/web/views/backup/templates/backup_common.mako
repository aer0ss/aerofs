<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Backup" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<%def name="html()">
    <%progress_modal:html>
        <p>
            Depending on your data size, backup might take some time...
        </p>
        ## Don't use <p> to wrap the following line to avoid an ugly, big padding
        ## between the line and the bottom of the modal.
        Please do not navigate away from this page.
        ## TODO (WW) add a browser alert when the user attempts to leave the page?
    </%progress_modal:html>
</%def>

<%def name="scripts(exitMaintenanceWhenDone)">
    <%progress_modal:scripts/>
    ## spinner support is required by progress_modal
    <%spinner:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
        });

        function hideProgressModal() {
            $('#${progress_modal.id()}').modal('hide');
        }

        ## Kick off the backup process. Call maintenanceExit() or download()
        ## once it's done.
        function backup() {
            $('#${progress_modal.id()}').modal('show');

            console.log("kickoff backup");
            $.post('${request.route_path('json_kickoff_backup')}', {
                ${csrf.token_param()}
            }).done(function(resp) {
                var next = ${'maintenanceExit' if exitMaintenanceWhenDone else 'download'};
                pollBootstrap(resp['bootstrap_execution_id'], next, fail);
            }).fail(fail);
        }

        ## Kick off maintenance exit. Call download() once it's done.
        function maintenanceExit() {
            console.log("kickoff maintenance-exit");
            $.post('${request.route_path('json_kickoff_maintenance_exit')}', {
                ${csrf.token_param()}
            }).done(function(resp) {
                pollBootstrap(resp['bootstrap_execution_id'], download, fail);
            }).fail(fail);
        }

        ## Direct the browser to download the file
        function download() {
            console.log("download ready");
            hideProgressModal();
            ## Since the link serves non-HTML content, the brower will
            ## start downloading without navigating away from the current page.
            window.location.href = '${request.route_path('download_backup_file')}';
        }

        function fail(xhr) {
            showErrorMessageFromResponse(xhr);
            hideProgressModal();
        }

        ## @param onFaiure it will be called with xhr as the parameter
        ##
        ## TODO (WW) share this code with apply_and_create_user_apge.mako
        function pollBootstrap(eid, onComplete, onFailure) {
            var interval = window.setInterval(function() {
                $.get('${request.route_path('json_poll_bootstrap')}', {
                    bootstrap_execution_id: eid
                }).done(function(resp) {
                    if (resp['status'] == 'success') {
                        console.log("bootstrap poll complete");
                        window.clearInterval(interval);
                        onComplete();
                    } else {
                        console.log("bootstrap poll in progress");
                        ## TODO (WW) add timeout?
                    }
                }).fail(function(xhr) {
                    console.log("bootstrap poll failed");
                    window.clearInterval(interval);
                    onFailure(xhr);
                });
            }, 1000);
        }
    </script>
</%def>