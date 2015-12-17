<%def name="scripts()">
    <script>

        ## Checks if the appliance is upgrading currently.
        ## If an upgrade is in progress displays/passes along the relevant modals depending
        ## upon what part of the upgrade process is currently in progress i.e. pulling new images
        ## or downloading backup file or garbage cleaning old images. The assumption is that if we
        ## are able to reach this page then the appliance is not switching/rebooting.
        ## Expected signatures:
        ##          modal pullWaitModal
        ##          function onPullCompletion()
        ##          modal pullFailModal
        ##          progress-bar progress_bar
        ##          function waitForBackup()
        ##          modal switchWaitFailModal
        ##          modal successModal
        ##          modal failModal
        ##          function onFailure(xhr)
        function checkUpgradeInProgressOrStartNew(pullWaitModal, onPullCompletion, pullFailModal,
                progress_bar, waitForBackup, switchWaitModal, successModal, failModal, onFailure) {
            $.get("${request.route_path('json-pull-images')}")
            .done(function(resp) {
                if (resp['running']) {
                    waitForPullImages(pullWaitModal, onPullCompletion, pullFailModal, progress_bar);
                } else {
                    $.get("${request.route_path('json-backup')}")
                    .done(function(resp) {
                        if (resp['running']) {
                            waitForBackup();
                        } else {
                            $.get("${request.route_path('json-gc')}")
                            .done(function(resp) {
                                if (resp['running']) {
                                    switchWaitModal.modal('show');
                                    waitForGC(switchWaitModal, successModal, failModal);
                                } else {
                                    pullImages($('#pull-wait-modal'), onPullCompletion, $('#pull-fail-modal'), $('.progress-bar'));
                                }
                            }).fail(function(xhr) {
                                if (onFailure) onFailure(xhr);
                            });
                        }
                    }).fail(function(xhr) {
                        if (onFailure) onFailure(xhr);
                    });
                }
            }).fail(function(xhr) {
                if (onFailure) onFailure(xhr);
            });
        }

        ## Checks if the appliance needs upgrading.
        ## Expected signatures:
        ##          function onSuccess()
        ##          function onFailure(xhr)
        function needsUpgrade(onSuccess, onFailure) {
            $.get("${request.route_path('json-needs-upgrade')}")
            .done(function(resp) {
                if (onSuccess) onSuccess(resp);
            }).fail(function(xhr) {
                if (onFailure) onFailure(xhr);
            });
        }

        ## Pulls latest images from registry.
        ## Expected signatures:
        ##          modal waitModal - display modal when pulling images.
        ##          function onPullCompletion - Method that must be called after pull images is done.
        ##          modal failModal - display modal when pull images fails.
        ##          progress_bar - progress bar to show upgrade progress.
        function pullImages(waitModal, onPullCompletion, failModal, progress_bar) {
            $.post("${request.route_path('json-pull-images')}")
            .done(function(resp) {
                if (resp["status_code"] == 200 || resp["status_code"] == 409) {
                    waitForPullImages(waitModal, onPullCompletion, failModal, progress_bar);
                } else {
                    showErrorMessage("Failed to upgrade: ".concat(resp["status_code"]));
                }
            }).fail(failed);
        }


        ## Check if we have fetched the latest images from registry and display appropriate
        ## modals in case of failure or move on to next step (the onPullCompletion method).
        ## Expected signatures:
        ##          modal waitModal - display modal when pulling is in progress.
        ##          function onPullCompletion - Method that must be called after pull images is done.
        ##          modal failModal - display modal when pull images fails.
        ##          progress_bar - progress bar to show upgrade progress.
        function waitForPullImages(waitModal, onPullCompletion, failModal, progress_bar) {
            waitModal.modal('show');
            console.log("wait for pulling images to finish");
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-pull-images')}")
                .done(function(resp) {
                    if (resp['running']) {
                        progress_bar.width((resp['pulling']/resp['total'] * 100) + "%");
                    } else if (resp['succeeded']) {
                        console.log('Pulled images');
                        waitModal.modal('hide');
                        window.clearInterval(interval);
                        if (onPullCompletion) onPullCompletion();
                    } else {
                        console.log('Pulling images failed');
                        window.clearInterval(interval);
                        waitModal.modal('hide');
                        failModal.modal('show');
                        showErrorMessage("Failed to upgrade");
                    }
                }).fail(failed);
            }, 5000);
        }



        function postGC(waitModal, successModal, failModal) {
            $.post("${request.route_path('json-gc')}")
            .done(function(resp) {
                if (resp["status_code"] == 200 || resp["status_code"] == 409) {
                    console.log('appliance switching complete. Now clean old images...');
                    waitForGC(waitModal, successModal, failModal);
                } else {
                    failModal.modal('show');
                }
            }).fail(failed);
        }

        ## Switch existing appliance to latest version and garbage clean old unused version images once switched.
        ## Expected signatures:
        ##          modal waitModal - display modal when appliance switching is in progress.
        ##          modal successModal - display modal both appliance switching + garbage cleaning is complete.
        ##          modal failModal - display modal when switching or garbage cleaning fails.
        function switchAppliance(waitModal, successModal, failModal) {
            waitModal.modal('show');
            console.log("wait for appliance switching")
            $.get("${request.route_path('json-get-boot')}")
            .done(function(resp) {
                var bootID = resp['id'];
                console.log("old bootid " + bootID);
                $.post("${request.route_path('json-switch-appliance')}")
                .done(function() {
                    console.log("switching appliance...");
                    waitForReboot(bootID, function() {
                        postGC(waitModal, successModal, failModal);
                    });
                }).fail(function(xhr, textStatus, errorThrown) {
                    ## Ignore errors as the server might be killed before replying
                    console.log("Ignore json-boot failure(while switching): " + xhr.status + " " + textStatus + " " + errorThrown);
                    waitForReboot(bootID, function() {
                        postGC(waitModal, successModal, failModal);
                    });
                });
            }).fail(failed);
        }

        ## Garbage clean once we switch over to the new appliance.
        ## Expected signatures:
        ##          modal waitModal - display modal to hide when gc is complete.
        ##          modal successModal - display modal when gc is complete.
        ##          modal failModal - display modal when gc fails.
        function waitForGC(waitModal, successModal, failModal) {
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-gc')}")
                .done(function(resp) {
                    if (resp['running']) {
                        console.log('cleaning images...');
                    } else if (resp['succeeded']) {
                        console.log('cleaned old images');
                        window.clearInterval(interval);
                        waitModal.modal('hide');
                        successModal.modal('show');
                    } else {
                        console.log('cleaning failed');
                        window.clearInterval(interval);
                        waitModal.modal('hide');
                        failModal.modal('show');
                    }
                }).fail(failed);
            }, 1000);
        }


        ## Reboot the app then call callback if it's non-null.
        ## Expected signatures:
        ##          function onSuccess()
        ##          function onFailure(xhr)
        function reboot(target, onSuccess, onFailure) {
            ## Get current boot ID before rebooting
            $.get("${request.route_path('json-get-boot')}")
            .done(function(resp) {
                var bootID = resp['id'];
                console.log("Reboot to /" + target + ". previous boot id: " + bootID);
                $.post("${request.route_path('json-boot', target='')}" + target)
                .done(function() {
                    waitForReboot(bootID, onSuccess);
                }).fail(function(xhr, textStatus, errorThrown) {
                    ## Ignore errors as the server might be killed before replying
                    console.log("Ignore json-boot failure: " + xhr.status + " " + textStatus + " " + errorThrown);
                    waitForReboot(bootID, onSuccess);
                });

            }).fail(function(xhr) {
                if (onFailure) onFailure(xhr);
            });
        }

        function waitForReboot(oldBootID, onSuccess) {
            console.log('Wait for reboot to finish');
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-get-boot')}")
                .done(function(resp) {
                    var bootID = resp['id'];
                    console.log("Boot id: " + bootID);

                    ## Track old vs new boot ID. Used to avoid race conditions where we check
                    ## system state before the box manages to go offline.
                    if (oldBootID != bootID) {
                        window.clearInterval(interval);
                        if (onSuccess) onSuccess();
                    }
                }).fail(function(xhr, textStatus, errorThrown) {
                    console.log("Ignore GET boot failure: " + xhr.status + " " + textStatus + " " + errorThrown);
                });
            }, 1000);
        }

    </script>
</%def>
