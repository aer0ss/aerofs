
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>
<%namespace name="bootstrap" file="bootstrap.mako"/>

<%def name="html()">
    <%progress_modal:html>
        Compressing log files...
    </%progress_modal:html>
</%def>

<%def name="scripts()">
    <%bootstrap:scripts/>
    <%progress_modal:scripts/>
    ## spinner support is required by progress_modal
    <%spinner:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
        });

        function archiveAndDownloadLogs() {
            $('#${progress_modal.id()}').modal('show');
            runBootstrapTask('archive-logs', downloadLogs, hideProgressModal);
        }

        ## Direct the browser to download the file
        function downloadLogs() {
            console.log("download ready");
            hideProgressModal();
            ## Since the link serves non-HTML content, the brower will
            ## start downloading without navigating away from the current page.
            ## Don't use "location.href =". It's not supported by old Firefox.
            window.location.assign('${request.route_path('download_logs')}');
        }

        function hideProgressModal() {
            $('#${progress_modal.id()}').modal('hide');
        }
    </script>
</%def>