<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Upgrade and backup" %>

<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>
<%namespace file="modal.mako" name="modal"/>
<%namespace name="loader" file="loader.mako"/>

<div class="page-block">
    <h2>Upgrade your AeroFS Appliance</h2>

    <p>You are running AeroFS appliance version <strong>${current_version}</strong>.
        You may check the latest appliance version and release notes on our
        <a href="https://support.aerofs.com/hc/en-us/articles/201439644" target="_blank">support site</a>.
    <p>
    Before you start upgrading your appliance to the latest version, please take a moment to read the
    following.</p>
    <p>The AeroFS appliance upgrade process consists of three steps:</p>
    <ol>
        <li>
            <p><strong>Download.</strong> The latest AeroFS appliance images are
            downloaded from registry.aerofs.com. This can take between one to two hours to complete.</p>
        </li>
        <li>
            <p><strong>Maintenance and backup</strong>. A backup file is downloaded, which can be used to manually
            upgrade the appliance in case automatic upgrade fails. To download the backup file
            your appliance must be put in Maintenance mode. As a result, all your AeroFS clients could
            pause syncing for up to half an hour.</p>

            <p>Click on "Manual Upgrade" below to find out more about how
            to manually upgrade your AeroFS appliance.</p>
        </li>
        <li>
             <p><strong>Switch</strong>. Your appliance is switched to the latest version.
             This will complete your upgrade. All your AeroFS clients should resume syncing again.
             This can take up to ten minutes to complete.
        </li>
    </ol>
    <p><strong> When the upgrade is done, AeroFS clients
        and Team Servers will automatically update in one hour.</strong></p>
</div>

<div class="page-block">
    <button class="btn btn-primary"
        onclick="upgradeIfHasDiskSpace(); return false;">
            Upgrade AeroFS
    </button>
</div>

<div class="page-block">
    <a id="show-manual-upgrade" href="#"
        onclick="showManualUpgrade(); return false;">
        Manual upgrade &#9656;</a>
    <a id="hide-manual-upgrade" href="#"
        onclick="hideManualUpgrade(); return true;" style="display: none;">
        Manual upgrade &#x25B4;</a>
</div>

<hr/>
<div class="page-block" id="manual-upgrade" style="display: none;">

    <p>Should automatic upgrade fail use the following steps to manually upgrade the appliance to the latest version
    via the backup file:</p>

    <ol>
        <li>
            Write down the network configuration found on your appliance console.
        </li>
        <li>
            Click the button below to put the appliance in Maintenance mode and download the backup file.
        </li>
        <li>
            Launch a new appliance image and select the restore option during setup. Redirecting
            DNS may be required.
        </li>
    </ol>
    <button class="btn btn-primary"
            onclick="backup(promptShutdown, $('#backup-only-progress-modal')); return false;">
        Put appliance in Maintenance mode and download backup file
    </button>
    <hr/>
</div>


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
                onclick="backup(maintenanceExit, $('#backup-only-progress-modal')); return false;">
            Download backup file
        </button>
    </p>
</div>

<div class="page-block">
    <p>
        Alternatively, you can download this <a onclick="downloadBackupScript($('#backup-only-progress-modal')); return false;">backup script</a> to download the backup file at your convenience.
    </p>
</div>

<hr/>

<%modal:modal>
    <%def name="id()">no-df-modal</%def>
    <%def name="title()">Low Disk Space On Appliance</%def>
    <p>
        Cannot start upgrade because your AeroFS appliance has less than 10GB free space.
        To start upgrade ensure that you have 10GB or more free space on your appliance
        and try again.
    </p>
</%modal:modal>

<%modal:modal>
    <%def name="id()">uptodate-modal</%def>
    <%def name="title()">Appliance up to date.</%def>
    <p>
        You are already running the latest AeroFS version.
    </p>
</%modal:modal>

## TODO(AS): This is such a shameful shameful hack, I want to curl up in a ball and cry a river.
## I have spent a lot of time, a lot of time trying to define these modals in loader.mako and make it work to
## no avail. However, sending them as args to functions defined in loader.mako seems to do the trick. Why? IDK.
<%modal:modal>
    <%def name="id()">success-modal</%def>
    <%def name="title()"><h4 class="text-success">Appliance Upgrade Succeeded</h4></%def>
    <p>
        You have successfully upgraded your AeroFS appliance to the latest version.
    </p>
</%modal:modal>

<%modal:modal>
    <%def name="id()">fail-modal</%def>
    <%def name="title()"><h4 class="text-error">Appliance Upgrade Failed</h4></%def>
    <p>
        AeroFS appliance upgrade failed. Please try again later.
    </p>
</%modal:modal>

<%modal:modal>
    <%def name="id()">pull-fail-modal</%def>
    <%def name="title()"><h4 class="text-error">Download Failed</h4></%def>
    <p>
        Failed to download latest AeroFS appliance images. Please try again later.
    </p>
</%modal:modal>

<%modal:modal>
    <%def name="id()">confirm-modal</%def>
    <%def name="title()"><h4 class="text-error">Confirm Upgrade</h4></%def>
    <p>
        Upgrading your AeroFS appliance can take between one to two hours. Your organization's
        AeroFS clients might pause syncing for up to half an hour during the end of this process.
    </p>
    <%def name="footer()">
        <a href="#" class="btn btn-default" data-dismiss="modal">Cancel</a>
        <a href="#" id="confirm-btn" class="btn btn-primary" data-dismiss="modal">Upgrade</a>
    </%def>
</%modal:modal>

<%progress_modal:progress_modal>
    <%def name="id()">switch-wait-modal</%def>
    <%def name="title()">Switching Appliance (Step 3/3)</%def>
    <%def name="no_close()"/>
    <p>
        Switching your appliance to the latest version now. This can
        take up to ten minutes.
        Please do not navigate away from this page...
    </p>
</%progress_modal:progress_modal>

<%modal:modal>
    <%def name="id()">pull-wait-modal</%def>
    <%def name="title()">Downloading the Latest AeroFS Version (Step 1/3)</%def>
    <%def name="no_close()"/>

    <p>
        Downloading the latest AeroFS appliance. This might take between one to two hours.
        Please do not navigate away from this page...
    </p>
    <%def name="footer()">
        <div class="progress">
            <div class="progress-bar" role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">
            </div>
        </div>
    </%def>
</%modal:modal>

<%modal:modal>
    <%def name="id()">shutdown-modal</%def>
    <%def name="title()">Please shut down appliance</%def>
    <%def name="no_close()"/>

    <p>
        After the download completes, please shut down the system and boot up a new appliance. This
        appliance is now in Maintenance Mode and no further operations should be made.
    </p>
</%modal:modal>

<%progress_modal:progress_modal>
    <%def name="id()">backup-only-progress-modal</%def>
    <%def name="no_close()"/>

    <p>
        Depending on your data size, backup might take up to fifteen minutes.
        Please do not navigate away from this page...
    </p>
</%progress_modal:progress_modal>

## TODO(AS): Blatant code duplication so pitiful. But as of right now
## I am not sure how to add the steps information without duplicating.
<%progress_modal:progress_modal>
    <%def name="id()">backup-upgrade-progress-modal</%def>
    <%def name="title()">Backing Up Your System (Step 2/3)</%def>
    <%def name="no_close()"/>

    <p>
        The appliance will now go into Maintenance mode. Depending on your data size,
        backup might take up to fifteen minutes. Please do not navigate away from this page...
    </p>
</%progress_modal:progress_modal>

<%block name="scripts">
    <%loader:scripts/>
    <%progress_modal:scripts/>
    <%spinner:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
            disableEscapingFromModal($('div.modal'));
        });

        $('#pull-wait-modal').modal({
            backdrop: 'static',
            keyboard: false,
            show: false
        })
        $('#switch-wait-modal').modal({
            backdrop: 'static',
            keyboard: false,
            show: false
        })
        $('#backup-only-progress-modal').modal({
            backdrop: 'static',
            keyboard: false,
            show: false
        })
        $('#backup-upgrade-progress-modal').modal({
            backdrop: 'static',
            keyboard: false,
            show: false
        })

        function showManualUpgrade() {
            $('#show-manual-upgrade').hide();
            $('#hide-manual-upgrade').show();
            $('#manual-upgrade').show();
        }

        function hideManualUpgrade() {
            $('#show-manual-upgrade').show();
            $('#hide-manual-upgrade').hide();
            $('#manual-upgrade').hide();
        }

        function onPullCompletion() {
            backup(switchToLatest, $('#backup-upgrade-progress-modal'));
        }

        ## Function that is called if a backup is in progress from a PRIOR
        ## upgrade attempt.
        function onBackupInProgress() {
            $('#backup-upgrade-progress-modal').modal('show');
            waitForBackup(switchToLatest, $('#backup-upgrade-progress-modal'));
        }

        function doUpgrade() {
            checkUpgradeInProgressOrStartNew($('#pull-wait-modal'), onPullCompletion, $('#pull-fail-modal'),
                    $('.progress-bar'), onBackupInProgress, $('#switch-wait-modal'), $('#success-modal'),
                    $('#fail-modal'), failed);
        }

        function confirmUpgrade() {
            var $modal = $('#confirm-modal');
             $('#confirm-btn').off().on('click', function() {
                 $modal.modal('hide');
                 doUpgrade();
             });
             $modal.modal('show');
        }

        function upgradeIfNotLatest() {
            needsUpgrade(function(resp) {
                if (resp['needs-upgrade']) {
                    console.log("needs upgrade");
                    confirmUpgrade();
                } else {
                    console.log("upgrade not needed");
                    $('#uptodate-modal').modal('show');
                }
            }, failed);
        }

        function upgradeIfHasDiskSpace() {
            $.get("${request.route_path('json-has-disk-space')}")
            .done(function(resp) {
                if (resp['has_disk_space']) {
                    upgradeIfNotLatest();
                } else {
                    $('#no-df-modal').modal('show');
                }
            }).fail(failed);
        }

        ## @param onBackupDone: a callback when backup succeeds. Expected signature:
        ##          function onBackupDone(onSuccess, onFailure),
        ## where onSuccess and onFailure are the methods to be called when onBackupDone succeeds or
        ## fails.
        ##
        ## When the backup is done, the system is in Maintenance Mode. It's onBackupDone()'s
        ## responsibility to exit the mode if necessary.
        function backup(onBackupDone, progressModal) {
            progressModal.modal('show');
            reboot('maintenance', function() {
                performBackup(onBackupDone, progressModal);
            }, failed);
        }

        function performBackup(onBackupDone, progressModal) {
            console.log("start backup");
            $.post("${request.route_path('json-backup')}")
            .done(function() {
                waitForBackup(onBackupDone, progressModal);
            }).fail(failed);
        }

        function waitForBackup(onBackupDone, progressModal) {
            console.log("wait for backup done");
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-backup')}")
                .done(function(resp) {
                    if (resp['running']) {
                        console.log('backup is running. wait');
                    } else if (resp['succeeded']) {
                        console.log('backup done');
                        window.clearInterval(interval);
                        if(onBackupDone)onBackupDone(function() {
                            download(progressModal);
                        });
                    } else {
                        console.log('backup failed');
                        window.clearInterval(interval);
                        hideProgressModal(progressModal);
                        showErrorMessage("Backup failed.");
                    }
                }).fail(failed);
            }, 1000);
        }

        ## Direct the browser to download the file
        function download(progressModal) {
            console.log("download ready");
            hideProgressModal(progressModal);

            ## Since the link serves non-HTML content, the brower will
            ## start downloading without navigating away from the current page.
            ## Don't use "location.href =". It's not supported by old Firefox.
            window.location.assign('${request.route_path("download_backup_file")}');
        }

        ## Direct the browser to download the script
        function downloadBackupScript(progressModal) {
            console.log("download ready");
            hideProgressModal(progressModal);
            ## Since the link serves non-HTML content, the brower will
            ## start downloading without navigating away from the current page.
            ## Don't use "location.href =". It's not supported by old Firefox.
            window.location.assign('${request.route_path("download_backup_script")}');
        }

        function failed(xhr) {
            hideProgressModal($('#backup-upgrade-progress-modal'));
            hideProgressModal($('#backup-only-progress-modal'));
            showErrorMessageFromResponse(xhr);
        }

        function hideProgressModal(progressModal) {
            progressModal.modal('hide');
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

        function switchToLatest(onSuccess) {
            onSuccess();
            console.log("start switching appliance");
            switchAppliance($('#switch-wait-modal'), $('#success-modal'), $('#fail-modal'));
        }

    </script>
</%block>

