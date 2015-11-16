<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Upgrade and backup" %>

<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>
<%namespace file="modal.mako" name="modal"/>
<%namespace name="loader" file="loader.mako"/>

<div class="page-block">
    <h2>Upgrade your AeroFS Appliance</h2>

    <p>This appliance is running version <strong>${current_version}</strong>.
        You may check the latest release notes on our
        <a href="https://support.aerofs.com/hc/en-us/articles/201439644" target="_blank">support site</a>.

    <p>To upgrade the appliance to a new version, please follow these steps:</p>

    <ol>
        <li>
            Write down the network configuration found on your appliance console.
        </li>
        <li>
            Click the button below to download the backup file and put the appliance in Maintenance
            Mode.
        </li>
        <li>
            Launch a new appliance image and select the restore option during setup. Redirecting
            DNS may be required.
        </li>
    </ol>
</div>

<div class="page-block">
    <p class="alert alert-success"><strong>Note</strong>: when the upgrade is
        done, AeroFS clients and Team Servers will automatically update
        in one hour.</p>
</div>

<div class="page-block">
    <button class="btn btn-primary"
            onclick="backup(promptShutdown); return false;">
        Download backup file and put appliance in Maintenance Mode
    </button>
</div>

<hr/>

<div class="page-block">
    <h2>Back up your AeroFS Appliance</h2>
    <p>
        You may back up your appliance for recovery later using the download button below. In the
        event that you need to restore your appliance from backup, follow the upgrade steps outlined
        above.
    </p>
</div>

<div class="page-block">
    <p class="alert alert-success">
        <strong>Note</strong>: the backup process may take a while. During this time, the system
        will enter Maintenance Mode.
    </p>
</div>

<div class="page-block">
    <p>
        <button class="btn btn-primary"
                onclick="backup(maintenanceExit); return false;">
            Download backup file
        </button>
    </p>
</div>

<div class="page-block">
    <p>
        Alternatively, you can download this <a onclick="downloadBackupScript(); return false;">backup script</a> to download the backup file at your convenience.
    </p>
</div>

<hr/>

<%modal:modal>
    <%def name="id()">shutdown-modal</%def>
    <%def name="title()">Please shut down appliance</%def>
    <%def name="no_close()"/>

    <p>
        After the download completes, please shut down the system and boot up a new appliance. This
        appliance is now in Maintenance Mode and no further operations should be made.
    </p>
</%modal:modal>

<%progress_modal:html>
    <p>
        Depending on your data size, backup might take some time...
    </p>
    ## Don't use <p> to wrap the following line to avoid an ugly, big padding between the line and
    ## the bottom of the modal.
    Please do not navigate away from this page.
    ## TODO (WW) add a browser alert when the user attempts to leave the page?
</%progress_modal:html>

<%block name="scripts">
    <%loader:scripts/>
    <%progress_modal:scripts/>
    <%spinner:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
            disableEscapingFromModal($('div.modal'));
        });

        ## @param onBackupDone: a callback when backup succeeds. Expected signature:
        ##          function onBackupDone(onSuccess, onFailure),
        ## where onSuccess and onFailure are the methods to be called when onBackupDone succeeds or
        ## fails.
        ##
        ## When the backup is done, the system is in Maintenance Mode. It's onBackupDone()'s
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

        ## Direct the browser to download the script
        function downloadBackupScript() {
            console.log("download ready");
            hideProgressModal();
            ## Since the link serves non-HTML content, the brower will
            ## start downloading without navigating away from the current page.
            ## Don't use "location.href =". It's not supported by old Firefox.
            window.location.assign('${request.route_path('download_backup_script')}');
        }

        function failed(xhr) {
            hideProgressModal();
            showErrorMessageFromResponse(xhr);
        }

        function hideProgressModal() {
            $('#${progress_modal.id()}').modal('hide');
        }

        function maintenanceExit(onSuccess, onFailure) {
            reboot('default', onSuccess, function(xhr) {
                showErrorMessageFromResponse(xhr);
                onFailure();
            });
        }

        function promptShutdown(onSuccess) {
            onSuccess();
            $('#shutdown-modal').modal('show');
        }
    </script>
</%block>
