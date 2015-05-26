
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>
<%namespace name="loader" file="loader.mako"/>

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

<%def name="scripts()">
    <%loader:scripts/>
    <%progress_modal:scripts/>
    ## spinner support is required by progress_modal
    <%spinner:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
        });

        ## @param onBackupDone: a callback when backup succeeds. Expected signature:
        ##          function onBackupDone(onSuccess, onFailure),
        ## where onSuccess and onFailure are the methods to be called when onBackupDone
        ## secceeds or fails.
        ##
        ## When the backup is done, the system is in maintenance mode. It's onBackupDone()'s
        ## responsibility to exit the mode if necessary.
        function backup(onBackupDone) {
            $('#${progress_modal.id()}').modal('show');
            reboot('maintenance', function() {
                performBackup(onBackupDone);
            }, failed);
        }

        function performBackup(onBackupDone) {
            console.log("start backup");
            $.post("${request.route_path('json-backup')}")
            .done(function() {
                waitForBackup(onBackupDone);
            }).fail(failed);
        }

        function waitForBackup(onBackupDone) {
            console.log("wait for backup done");
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-backup')}")
                .done(function(resp) {
                    if (resp['running']) {
                        console.log('backup is running. wait');
                    } else if (resp['succeeded']) {
                        console.log('backup done');
                        window.clearInterval(interval);
                        onBackupDone(download, hideProgressModal);
                    } else {
                        console.log('backup failed');
                        window.clearInterval(interval);
                        hideProgressModal();
                        showErrorMessage("Backup failed.");
                    }
                }).fail(failed);
            }, 1000);
        }

        ## Direct the browser to download the file
        function download() {
            console.log("download ready");
            hideProgressModal();
            ## Since the link serves non-HTML content, the brower will
            ## start downloading without navigating away from the current page.
            ## Don't use "location.href =". It's not supported by old Firefox.
            window.location.assign('${request.route_path('download_backup_file')}');
        }

        function failed(xhr) {
            hideProgressModal();
            showErrorMessageFromResponse(xhr);
        }

        function hideProgressModal() {
            $('#${progress_modal.id()}').modal('hide');
        }
    </script>
</%def>