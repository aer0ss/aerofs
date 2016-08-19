
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<%def name="html()">
    <%progress_modal:progress_modal>
        <%def name="id()">logs-modal</%def>
        Compressing appliance logs. This process might take some time.
    </%progress_modal:progress_modal>
</%def>

<%def name="submit_logs_text()">
    <a href="https://support.aerofs.com/hc/en-us/articles/201439634-AeroFS-Customer-Service-Levels"
            target="_blank">Contact us</a>
    with a description of the problem, and include recent appliance logs.
</%def>

<%def name="scripts()">
    <%progress_modal:scripts/>
    ## spinner support is required by progress_modal
    <%spinner:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
        });

        $('#logs-modal').modal({
            backdrop: 'static',
            keyboard: false,
            show: false
        });

        function archiveAndDownloadLogs() {
            $('#logs-modal').modal('show');
            $.post('${request.route_path("json-archive-container-logs")}')
                .done(downloadLogs)
                .fail(function(xhr) {
                    showErrorMessageFromResponse(xhr);
                    hideProgressModal();
                });
        }

        ## Direct the browser to download the file
        function downloadLogs() {
            console.log("download ready");
            hideProgressModal();
            ## Since the link serves non-HTML content, the browser will
            ## start downloading without navigating away from the current page.
            ## Don't use "location.href =". It's not supported by old Firefox.
            window.location.assign('${request.route_path('download_logs')}');
        }

        function hideProgressModal() {
            $('#logs-modal').modal('hide');
        }
    </script>
</%def>
