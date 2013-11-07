<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Backup" %>

<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>
<%namespace name="bootstrap" file="bootstrap.mako"/>

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
    <%bootstrap:scripts/>
    <%progress_modal:scripts/>
    ## spinner support is required by progress_modal
    <%spinner:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
        });

        ## Run backup process. Call maintenanceExit() or download()
        ## once it's done.
        function backup() {
            $('#${progress_modal.id()}').modal('show');

            var next = ${'maintenanceExit' if exitMaintenanceWhenDone else 'download'};
            runBootstrapTask('db-backup', next, hideProgressModal);
        }

        ## Maintenance exit. Call download() once it's done.
        function maintenanceExit() {
            runBootstrapTask('maintenance-exit', download, hideProgressModal);
        }

        ## Direct the browser to download the file
        function download() {
            console.log("download ready");
            hideProgressModal();
            ## Since the link serves non-HTML content, the brower will
            ## start downloading without navigating away from the current page.
            window.location.href = '${request.route_path('download_backup_file')}';
        }

        function hideProgressModal() {
            $('#${progress_modal.id()}').modal('hide');
        }
    </script>
</%def>