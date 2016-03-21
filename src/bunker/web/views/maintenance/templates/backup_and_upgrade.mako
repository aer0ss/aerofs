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
            <p><strong>Maintenance and backup</strong>. An appliance backup file is downloaded, which can
            be used to manually upgrade the appliance in case automatic upgrade fails. To download the
            backup file your appliance must be put in Maintenance mode. As a result, all your AeroFS
            clients could pause syncing for up to half an hour.</p>

            <p>Before you can proceed to the next step, you will be asked to confirm if you have
            completed downloading the backup file. Please confirm only when you have <strong>finished</strong>
            downloading the file.</p>

            <p>Click on "Manual Upgrade" below to find out more about how
            to manually upgrade your AeroFS appliance.</p>
        </li>
        <li>
             <p><strong>Switch</strong>. Your appliance is switched to the latest version.
             This will complete your upgrade. All your AeroFS clients should resume syncing again.
             This can take up to twenty minutes to complete.
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


<div class="page-block"
    %if not os_upgrade_enabled:
        hidden
    %endif
>
    <h2>Upgrade your AeroFS appliance's host VM</h2>
    <p>
        You can upgrade the operating system of the AeroFS appliance host by clicking the button below.
        <strong>Currently, this is only available for those AeroFS appliances whose host operating system is CoreOS.</strong>
    </p>
    <p>

        This is especially useful in case of critical updates to CoreOS. Upgrading the OS should
        take about fifteen minutes, during which all AeroFS clients in your organization
        will pause syncing.
    </p>
    <button class="btn btn-primary"
            onclick="osUpgrade(onOSUpgradeSuccess, onOSUpgradeFailure); return false;">
        Upgrade OS
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
                onclick="backupAndRestoreToCurrentTarget(); return false;">
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

<%modal:modal>
    <%def name="id()">wrong-os-modal</%def>
    <%def name="title()">Cannot Upgrade AeroFS Appliance Host VM.</%def>
    <p>
        Your AeroFS is not running on CoreOS. Cannot upgrade.
    </p>
</%modal:modal>

<%modal:modal>
    <%def name="id()">os-success-modal</%def>
    <%def name="title()"><h4 class="text-success">OS Upgrade Succeeded</h4></%def>
    <p>
        You have successfully upgraded the operating system of your AeroFS appliance host.
    </p>
</%modal:modal>

<%modal:modal>
    <%def name="id()">os-fail-modal</%def>
    <%def name="title()"><h4 class="text-error">OS Upgrade Failed</h4></%def>
    <p>
        Failed to upgrade the operating system of your AeroFS appliance host. Please try again later.
        All AeroFS clients in your organization should still be able to resume syncing.
    </p>
</%modal:modal>

<%modal:modal>
    <%def name="id()">upgrade-confirm-modal</%def>
    <%def name="title()"><h4 class="text-error">Confirm Upgrade</h4></%def>
    <p>
        Upgrading your AeroFS appliance can take between one to two hours. Your organization's
        AeroFS clients might pause syncing for up to half an hour during the end of this process.
    </p>
    <%def name="footer()">
        <a href="#" class="btn btn-default" data-dismiss="modal">Cancel</a>
        <a href="#" id="upgrade-confirm-btn" class="btn btn-primary" data-dismiss="modal">Upgrade</a>
    </%def>
</%modal:modal>

<%modal:modal>
    <%def name="id()">backup-start-confirm-modal</%def>
    <%def name="title()"><h4 class="text-error">Start Backup</h4></%def>
    <%def name="no_close()"/>
    <p>
        Are you sure you want to put your appliance in maintenance mode and start
        backup? The backup process can take up to fifteen minutes at the completion of which, your browser
        will automatically start downloading the backup file.

        While in maintenance mode, your organization's AeroFS clients will temporarily stop syncing
        data.
    </p>
    <%def name="footer()">
        <a href="#" id="backup-start-confirm-btn" class="btn btn-primary"
            data-dismiss="modal">Put my appliance in maintenance mode and start backup</a>
    </%def>

</%modal:modal><%modal:modal>
    <%def name="id()">backup-done-confirm-modal</%def>
    <%def name="title()"><h4 class="text-error">Confirm Backup File Download</h4></%def>
    <%def name="no_close()"/>
    <p>
        Your browser should have started downloading the backup file automatically. Before we can
        switch your appliance to the latest version, please confirm that you have
        <strong>completed</strong> downloading the appliance backup file.
    </p>
    <%def name="footer()">
        <a href="#" id="backup-done-confirm-btn" class="btn btn-primary"
            data-dismiss="modal">Yes, I have downloaded the backup file</a>
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

<%modal:modal>
    <%def name="id()">upgrade-os-modal</%def>
    <%def name="title()"><h4 class="text-error">Confirm OS upgrade</h4></%def>
    <p>
        Upgrading the OS should take about fifteen minutes, during which all AeroFS clients in your organization
        will pause syncing.
    </p>
    <%def name="footer()">
        <a href="#" id="upgrade-os-cancel-btn" class="btn btn-default" data-dismiss="modal">Cancel</a>
        <a href="#" id="upgrade-os-btn" class="btn btn-primary" data-dismiss="modal">Upgrade OS</a>
    </%def>
</%modal:modal>

<%progress_modal:progress_modal>
    <%def name="id()">os-upgrade-progress-modal</%def>
    <%def name="title()"><h4 class="text-error">Upgrading Your OS...</h4></%def>
    <%def name="no_close()"/>

    <p>
        Upgrading your OS can take upto fifteen minutes.
        Please do not navigate away from this page...
    </p>
</%progress_modal:progress_modal>

<%block name="scripts">
    <%loader:modals/>
    <%loader:scripts/>
    <%progress_modal:scripts/>
    <%spinner:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
            disableEscapingFromModal($('div.modal'));
        });

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
            var $modal = $('#backup-start-confirm-modal');
            $('#backup-start-confirm-btn').off().on('click', function() {
                $modal.modal('hide');
                console.log("start appliance backup");
                backup(switchToLatest, $('#backup-upgrade-progress-modal'));
            });
            $modal.modal('show');
        }

        ## Function that is called if a backup is in progress from a PRIOR
        ## upgrade attempt.
        function onBackupInProgress() {
            $('#backup-upgrade-progress-modal').modal('show');
            waitForBackup(switchToLatest, $('#backup-upgrade-progress-modal'));
        }

        ## Function that starts a new upgrade if no upgrade in progress,
        ## else resume currently running upgrade.
        function resumeOrStartNewUpgrade() {
            ## Upgrade process consists of pulling new images, backing up, switching
            ## repackaging or GC'ing old images. Check where we are in the process and
            ## resume from that stage. In case of no running upgrades, start a new upgrade process.
            try {
                if (isInProgress("${request.route_path('json-pull-images')}", handleFailed)) {
                    console.log("resume from pulling images");
                    waitForPullImages(onPullCompletion, handleFailed);
                    return;
                }
                if (isInProgress("${request.route_path('json-backup')}", handleFailed)) {
                    console.log("resume from backing up ");
                    onBackupInProgress();
                    return;
                }
                if (isInProgress("${request.route_path('json-repackaging')}", handleFailed)) {
                    console.log("resume from repackaging");
                    waitForRepackaging(handleFailed);
                    return;
                }
                if (isInProgress("${request.route_path('json-gc')}", handleFailed)) {
                    console.log("resume from gc");
                    waitForGC(handleFailed);
                    return;
                }
                pullImages(onPullCompletion, handleFailed);
            } catch(err) {
                console.log(err);
            }
        }

        function confirmOSUpgrade(onSucess, onFailure) {
            var $modal = $('#upgrade-os-modal');
            $('#upgrade-os-btn').off().on('click', function() {
                $('#os-upgrade-progress-modal').modal('show');
                $modal.modal('hide');
                upgradeOS(onSuccess, onFailure, handleFailed);
            });
            $modal.modal('show');
        }

        function osUpgrade(onSuccess, onFailure) {
            canUpgradeOS(function(resp) {
                if (resp['can_upgrade_os']) {
                    console.log("can upgrade os");
                    confirmOSUpgrade(onSuccess, onFailure);
                } else {
                    console.log("cannot upgrade os");
                    $('#wrong-os-modal').modal('show');
                }
            }, handleFailed);
        }

        function confirmUpgrade() {
            var $modal = $('#upgrade-confirm-modal');
            $('#upgrade-confirm-btn').off().on('click', function() {
                $modal.modal('hide');
                resumeOrStartNewUpgrade();
            });
            $modal.modal('show');
        }

        function onOSUpgradeSuccess(target) {
            reboot(target, function() {
                $('#os-upgrade-progress-modal').modal('hide');
                $('#os-success-modal').modal('show');
            }, handleFailed);
        }

        function onOSUpgradeFailure(target) {
            reboot(target, function() {
                $('#os-upgrade-progress-modal').modal('hide');
                $('#os-fail-modal').modal('show');
            }, handleFailed);
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
            }, handleFailed);
        }

        function upgradeIfHasDiskSpace() {
            $.get("${request.route_path('json-has-disk-space')}")
            .done(function(resp) {
                if (resp['has_disk_space']) {
                    upgradeIfNotLatest();
                } else {
                    $('#no-df-modal').modal('show');
                }
            }).fail(handleFailed);
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
            }, handleFailed);
        }

        function performBackup(onBackupDone, progressModal) {
            console.log("start backup");
            $.post("${request.route_path('json-backup')}")
            .done(function() {
                waitForBackup(onBackupDone, progressModal);
            }).fail(handleFailed);
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
                        }, handleFailed);
                    } else {
                        console.log('backup failed');
                        window.clearInterval(interval);
                        hideProgressModal(progressModal);
                        showErrorMessage("Backup failed.");
                    }
                }).fail(handleFailed);
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

        function handleFailed(xhr) {
            hideProgressModal($('#backup-upgrade-progress-modal'));
            hideProgressModal($('#backup-only-progress-modal'));
            showErrorMessageFromResponse(xhr);
        }

        function hideProgressModal(progressModal) {
            progressModal.modal('hide');
        }

        function backupAndRestoreToCurrentTarget() {
            $.get("${request.route_path('json-get-boot')}")
            .done(function(resp) {
                var target = resp['id'];
                console.log("will reboot to " + target);
                backup(rebootToTargetFunction(target), $('#backup-only-progress-modal'));
            }).fail(function () {
                console.log("get boot ID req failed");
                showErrorMessage("Could not backup and restore, please check logs.");
            });
        }

        function rebootToTargetFunction(target) {
            var ret = function(onSuccess, onFailure) {
                reboot(target, onSuccess, function(xhr) {
                    showErrorMessageFromResponse(xhr);
                    onFailure();
                });
            }
            return ret;
        }

        function promptShutdown(onSuccess) {
            onSuccess();
            $('#shutdown-modal').modal('show');
        }

        function switchToLatest(onSuccess, onFailure) {
            onSuccess();
            var $modal = $('#backup-done-confirm-modal');
            $('#backup-done-confirm-btn').off().on('click', function() {
                $modal.modal('hide');
                console.log("start switching appliance");
                switchAppliance(onFailure);
            });
            $modal.modal('show');
        }

    </script>
</%block>

