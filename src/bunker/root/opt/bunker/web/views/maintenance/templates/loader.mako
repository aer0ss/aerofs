<%namespace name="progress_modal" file="progress_modal.mako"/>
<%namespace file="modal.mako" name="modal"/>

<%def name="modals()">

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
    <%def name="id()">on-latest-os-modal</%def>
    <%def name="title()"><h4 class="text-success">Already On Latest OS</h4></%def>
    <p>
        You AeroFS is already hosted on the latest stable CoreOS version.
    </p>
</%modal:modal>

<%modal:modal>
    <%def name="id()">pull-fail-modal</%def>
    <%def name="title()"><h4 class="text-error">Download Failed</h4></%def>
    <p>
        Failed to download latest AeroFS appliance images. Please try again later.
    </p>
</%modal:modal>

<%progress_modal:progress_modal>
    <%def name="id()">switch-wait-modal</%def>
    <%def name="title()">Switching Appliance (Step 3/3)</%def>
    <%def name="no_close()"/>
    <p>
        Switching your appliance to the latest version now. This can
        take up to twenty minutes.
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

</%def>

<%def name="scripts()">
    <script>

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

        function canUpgradeOS(onSuccess, onFailure) {
            $.get("${request.route_path('json-can-upgrade-os')}")
            .done(function(resp) {
                if (onSuccess) onSuccess(resp);
            }).fail(function(xhr) {
                if (onFailure) onFailure(xhr);
            });
        }

        function isInProgress(route, onFailure) {
            $.get(route)
            .done(function(resp) {
                return resp['running']
            }).fail(function(xhr) {
                if (onFailure) {
                    onFailure(xhr);
                    throw "Couldn't complete request: " + route;
                }
            });
        }

        ## Pulls latest images from registry.
        ## Expected signatures:
        ##          function onPullCompletion - Method that must be called after pull images is done.
        function pullImages(onPullCompletion, handleFailed) {
            $.post("${request.route_path('json-pull-images')}")
            .done(function(resp) {
                if (resp["status_code"] == 200 || resp["status_code"] == 409) {
                    waitForPullImages(onPullCompletion, handleFailed);
                } else {
                    showErrorMessage("Failed to upgrade: ".concat(resp["status_code"]));
                }
            }).fail(function(xhr) {
                if (handleFailed) handleFailed(xhr);
            });
        }


        ## Check if we have fetched the latest images from registry and display appropriate
        ## modals in case of failure or move on to next step (the onPullCompletion method).
        ## Expected signatures:
        ##          function onPullCompletion - Method that must be called after pull images is done.
        function waitForPullImages(onPullCompletion, handleFailed) {
            $('#pull-wait-modal').modal('show');
            console.log("wait for pulling images to finish");
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-pull-images')}")
                .done(function(resp) {
                    if (resp['running']) {
                        $('.progress-bar').width((resp['pulling']/resp['total'] * 100) + "%");
                    } else if (resp['succeeded']) {
                        console.log('Pulled images');
                        $('#pull-wait-modal').modal('hide');
                        window.clearInterval(interval);
                        if (onPullCompletion) onPullCompletion();
                    } else {
                        console.log('Pulling images failed');
                        window.clearInterval(interval);
                        $('#pull-wait-modal').modal('hide');
                        $('#pull-fail-modal').modal('show');
                        showErrorMessage("Failed to upgrade");
                    }
                }).fail(function(xhr) {
                    window.clearInterval(interval);
                    if (handleFailed) handleFailed(xhr);
                });
            }, 5000);
        }

        function postRepackage(handleFailed) {
            ## Delete the repackaging done file so that we can repackage.
            $.post("${request.route_path('json-del-repackage-done')}")
            .done(function(resp) {
                $.post("${request.route_path('json-repackaging')}")
                .done(function(resp) {
                    console.log("Repackaging installers...");
                    waitForRepackaging(handleFailed);
                }).fail(function(xhr) {
                    window.clearInterval(interval);
                    if (handleFailed) handleFailed(xhr);
                });
            }).fail(function(xhr) {
                    window.clearInterval(interval);
                    if (handleFailed) handleFailed(xhr);
            });
        }

        function waitForRepackaging(handleFailed) {
            console.log('Wait for repackaging to finish');
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-repackaging')}")
                .done(function(resp) {
                    if (resp['running']) {
                        console.log('packaging is running. wait');
                    } else if (resp['succeeded']) {
                        console.log('packaging is done');
                        window.clearInterval(interval);
                        postGC(handleFailed);
                    } else {
                        window.clearInterval(interval);
                        console.log('repackaging has not succeeded');
                        $('#fail-modal').modal('show');
                    }
                }).fail(function(xhr) {
                    window.clearInterval(interval);
                    handleFailed(xhr);
                });
            }, 1000);
        }

        function postGC(handleFailed) {
            $.post("${request.route_path('json-gc')}")
            .done(function(resp) {
                if (resp["status_code"] == 200 || resp["status_code"] == 409) {
                    console.log('appliance switching complete. Now clean old images...');
                    waitForGC(handleFailed);
                } else {
                    $('#fail-modal').modal('show');
                }
            }).fail(function(xhr) {
                if (handleFailed) handleFailed(xhr);
            });
        }

        ## Garbage clean once we switch over to the new appliance.
        function waitForGC(handleFailed) {
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-gc')}")
                .done(function(resp) {
                    if (resp['running']) {
                        console.log('cleaning images...');
                    } else if (resp['succeeded']) {
                        console.log('cleaned old images');
                        window.clearInterval(interval);
                        $('#switch-wait-modal').modal('hide');
                        $('#success-modal').modal('show');
                    } else {
                        console.log('cleaning failed');
                        window.clearInterval(interval);
                        $('#switch-wait-modal').modal('hide');
                        $('#fail-modal').modal('show');
                    }
                }).fail(function(xhr) {
                    window.clearInterval(interval);
                    if (handleFailed) handleFailed(xhr);
                });
            }, 1000);
        }

        ## Switch existing appliance to latest version and garbage clean old unused version images once switched.
        function switchAppliance(target, handleFailed) {
            $('#switch-wait-modal').modal('show');
            console.log("wait for appliance switching")
            $.get("${request.route_path('json-get-boot')}")
            .done(function(resp) {
                var bootID = resp['id'];
                console.log("old bootid " + bootID);
                $.post("${request.route_path('json-switch-appliance', target='')}" + target)
                .done(function() {
                    console.log("switching appliance...");
                    waitForReboot(bootID, function() {
                        postRepackage(handleFailed);
                    });
                }).fail(function(xhr, textStatus, errorThrown) {
                    ## Ignore errors as the server might be killed before replying
                    console.log("Ignore json-boot failure(while switching): " + xhr.status + " " + textStatus + " " + errorThrown);
                    waitForReboot(bootID, function() {
                        postRepackage(handleFailed);
                    });
                });
            }).fail(function(xhr) {
                if (handleFailed) handleFailed(xhr);
            });
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

        function upgradeOS(onSuccess, onFailure, handleFailed) {
            $.get("${request.route_path('json-get-boot')}")
            .done(function(resp) {
                var target = resp['target'];
                reboot('maintenance', function() {
                    $.post("${request.route_path('json-update-os')}")
                    .done(function() {
                        if (resp["status_code"] == 200 || resp["status_code"] == 409) {
                            waitForUpgradeOS(target, onSuccess, onFailure, handleFailed);
                        } else {
                            showErrorMessage("Failed to upgrade: ".concat(resp["status_code"]));
                        }
                    }).fail(handleFailed)
                }, handleFailed);
            }).fail(function(xhr) {
                if (handleFailed) handleFailed(xhr);
            });
        }

        function waitForUpgradeOS(target, onSuccess, onFailure, handleFailed) {
            console.log('Wait for os upgrade');
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-update-os')}")
                .done(function(resp) {
                    if (resp['running']) {
                        console.log('downloading new os version...');
                    } else if (resp['succeeded']) {
                        console.log('downloaded new os version');
                        window.clearInterval(interval);
                        ## Only reboot if not on latest.
                        if (!resp['latest']) {
                            rebootVM(target, onSuccess, handleFailed);
                        } else {
                            reboot(target, function() {
                                $('#os-upgrade-progress-modal').modal('hide');
                                $('#on-latest-os-modal').modal('show');
                                }, handleFailed);
                        }
                    } else {
                        console.log('download OS failed');
                        window.clearInterval(interval);
                        if (onFailure) onFailure(target);
                    }
                }).fail(handleFailed);
            }, 5000);
        }

        function rebootVM(target, onSuccess, handleFailed) {
            $.get("${request.route_path('json-get-boot')}")
            .done(function(resp) {
                var bootID = resp['id'];
                console.log("Reboot to /" + target + ". previous boot id: " + bootID);
                $.post("${request.route_path('json-reboot-vm')}")
                .done(function() {
                    waitForReboot(bootID, function() {
                        onSuccess(target);
                    });
                }).fail(function(xhr, textStatus, errorThrown) {
                    ## Ignore errors as the server might be killed before replying
                    console.log("Ignore json-boot failure: " + xhr.status + " " + textStatus + " " + errorThrown);
                    waitForReboot(bootID, function() {
                        onSuccess(target);
                    });
                });
            }).fail(handleFailed);
        }

</script>
</%def>
