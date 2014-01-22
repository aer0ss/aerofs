
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>
<%namespace name="bootstrap" file="bootstrap.mako"/>

<%def name="html()">
    <%progress_modal:html>
        Compressing appliance logs. This process might take some time.
    </%progress_modal:html>
</%def>

<%def name="submit_logs_text(open_link_in_new_window)">
    <strong>We are here to help!</strong> Submit your logs at
    <a href="https://support.aerofs.com/anonymous_requests/new"
        %if open_link_in_new_window:
            target="_blank"
        %endif
    >
    support.aerofs.com</a> with a brief description of the problem. We will
    get back to you within one business day!
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
            ## Since the link serves non-HTML content, the browser will
            ## start downloading without navigating away from the current page.
            ## Don't use "location.href =". It's not supported by old Firefox.
            window.location.assign('${request.route_path('download_logs')}');
        }

        function hideProgressModal() {
            $('#${progress_modal.id()}').modal('hide');
        }
    </script>
</%def>
