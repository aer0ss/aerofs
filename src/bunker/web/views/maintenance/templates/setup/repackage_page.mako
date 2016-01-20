<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="loader" file="../loader.mako"/>
<%namespace name="common" file="setup_common.mako"/>
<%namespace name="spinner" file="../spinner.mako"/>
<%namespace name="progress_modal" file="../progress_modal.mako"/>

<h4>Repackage installers</h4>

<p>Custom desktop installer packages specific to your installation will be
   generated. This might take a short while. Please do not close this browser window.</p>

<form method="POST" role="form" onsubmit="submitForm(); return false;">
    ${common.render_finish_prev_buttons()}
</form>

<%progress_modal:progress_modal>
    <%def name="id()">repackage-modal</%def>
    <span id="be-patient-text">Please wait while the system customizes your
    desktop client installers...</span>
</%progress_modal:progress_modal>

<div id="success-modal" class="modal" tabindex="-1" role="dialog">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h4 class="text-success">The system is ready!</h4>
            </div>
            <div class="modal-body">
                <% first_user_created = is_configuration_completed or restored_from_backup %>

                %if first_user_created:
                    <p>System configuration is complete.</p>
                %else:
                    <p>You may now create the system's first user.</p>
                %endif

            </div>
            <div class="modal-footer">
                <%
                    home_url = 'https://' + str(current_config['base.host.unified'])
                    # Use the SMTP verification email as the default first user email.
                    email = str(current_config['last_smtp_verification_email'])
                %>

                %if first_user_created:
                    <a class="btn btn-primary" href='${home_url}'>Go to Home Page</a>
                %else:
                    <a class="btn btn-primary" href='${home_url}/create_first_user?email=${email | u}'>
                        Create First User</a>
                %endif
            </div>
        </div>
    </div>
</div>

<%def name="scripts()">
    <%progress_modal:scripts/>
    <%spinner:scripts/>
    <%loader:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
            ## Disalbe esaping from all modals
            disableEscapingFromModal($('div.modal'));
        });

        $('#repackage-modal').modal({
            backdrop: 'static',
            keyboard: false,
            show: false
        });

        function resume() {
            ${common.trackInitialTrialSetup('Wait for services')}
            $('#repackage-modal').modal('show');
            waitForServicesReady();
        }


        function submitForm() {
            resume();
        }

        function waitForServicesReady() {
            console.log('wait for all services to be ready');
            var count = 0;
            var poll = function() {
                $.get("${request.route_path('json-status')}")
                .done(function (resp) {
                    count++;
                    for (var i = 0; i < resp['statuses'].length; i++) {
                        var status = resp['statuses'][i];
                        var service = status['service'];
                        ## Ignores Team Server errors as these are not relevant
                        if (service == 'team-servers') continue;

                        if (!status['is_healthy']) {
                            console.log('service ' + service + ' is unhealthy.');
                            ## Wait for ~10 seconds
                            if (count > 10) {
                                console.log('waitForHealthyServices() timed out');
                                hideAllModals();
                                showLogPromptWithMessageUnsafe('Service ' + service + ' failed to start.');
                                trackError();
                            } else {
                                console.log('wait');
                                window.setTimeout(poll, 1000);
                            }
                            return;
                        }
                    }

                    ## All services are ready
                    waitForPreviousRepackaging();

                }).fail(onFailure);
            };

            poll();
        }

        ## I know we just booted up but someone else might kicked off repackaging right before us.
        function waitForPreviousRepackaging() {
            console.log('wait for previous repackaging to finish');
            ## Wait for previouew repackaging done
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-repackaging')}")
                    .done(function (resp) {
                        if (resp['running']) {
                            console.log('previous packaging is running. wait');
                        } else {
                            window.clearInterval(interval);
                            repackage();
                        }
                    }).fail(function(xhr, textStatus, errorThrown) {
                        ## Ignore failures as Repackaging might not have started
                        console.log("ignore GET repackaging failure: " + xhr.status + " " +textStatus + " " +
                            errorThrown);
                    });
            }, 1000);
        }

        function repackage() {
            console.log('kick off repackaging');
            $.post("${request.route_path('json-repackaging')}")
            .done(waitForRepackaging)
            .fail(onFailure);
        }

        function waitForRepackaging() {
            console.log('wait for repackaging to finish');
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-repackaging')}")
                .done(function(resp) {
                    if (resp['running']) {
                        console.log('packaging is running. wait');
                    } else if (resp['succeeded']) {
                        window.clearInterval(interval);
                        conclude();
                    } else {
                        window.clearInterval(interval);
                        console.log('repackaging has not succeeded');
                        hideAllModals();
                        showLogPromptWithMessageUnsafe("Repackaging of AeroFS clients couldn't complete.");
                        trackError();
                    }
                }).fail(function(xhr) {
                    window.clearInterval(interval);
                    onFailure(xhr);
                });
            }, 1000);
        }

        function conclude() {
            console.log('create conf-initialized flag');
            $.post("${request.route_path('json-set-configuration-completed')}")
            .done(function() {
                ## TODO: wait for web to fully launch before prompting the user to navigate
                ## to there.
                hideAllModals();
                $('#success-modal').modal('show');
                trackSuccessAndDisableDataCollection();

            }).fail(onFailure);
        }

        function onFailure(xhr) {
            console.log('failed');
            hideAllModals();
            showAndTrackErrorMessageFromResponse(xhr);
        }

        function trackSuccessAndDisableDataCollection() {
            ## Report the last event and then disable data collection. You may
            ## ask, why not use is_configuration_completed() to disable data
            ## collection? It's because before this page reloads itself the
            ## system may mark configuration as initialized, and thus disable
            ## tracking too early.
            ##
            ## Call the methods below asynchronously. Ignore errors
            ${common.trackInitialTrialSetup('Completed Setup')}
            $.post("${request.route_path('json_setup_disable_data_collection')}");
        }
    </script>
</%def>
