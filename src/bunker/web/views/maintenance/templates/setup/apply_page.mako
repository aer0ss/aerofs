<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="loader" file="../loader.mako"/>
<%namespace name="common" file="setup_common.mako"/>
<%namespace name="spinner" file="../spinner.mako"/>
<%namespace name="progress_modal" file="../progress_modal.mako"/>

## N.B. When adding or removing content, adjust the modals' "top" style
## to match the content's position.

<p>Your changes will be applied to various AeroFS system components.
    This might take a short while.</p>
<hr />

${common.render_previous_button()}
<button
    onclick='apply(); return false;' id="finish-btn"
    class='btn btn-primary pull-right'>Apply and Finish</button>

<%progress_modal:html>
    <span id="count-down-text">Please wait for about
        <strong><span id="count-down-number"></span></strong>
        seconds while the system initializes services and customizes desktop
        clients...</span>
        <span id="be-patient-text">It should be done very shortly...</span>
</%progress_modal:html>

<div id="success-modal" class="modal" tabindex="-1" role="dialog">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h4 class="text-success">The system is ready!</h4>
            </div>
            <div class="modal-body">
                <% first_user_created = is_configuration_initialized or restored_from_backup %>

                %if first_user_created:
                    <p>System configuration is complete.</p>
                %else:
                    <p>Next, you will create the system's first user.</p>
                    <p>Your browser may warn about the certificate if you chose a self-signed certificate
                        in the previous step.</p>
                %endif

            </div>
            <div class="modal-footer">

                <%
                    # Redirect user to the set hostname rather than the hostname derived from the current URL which can be an IP
                    # address. This is useful to suppress browser warnings if the CNAME of the browser certificate doesn't match
                    # the IP address.
                    home_url = 'https://' + current_config['base.host.unified']
                    # Use the SMTP verification email as the default first user email
                    email = current_config['last_smtp_verification_email']
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
    ## spinner support is required by progress_modal
    <%spinner:scripts/>
    <%loader:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
            ## Disalbe esaping from all modals
            disableEscapingFromModal($('div.modal'));
            initializeModals();
        });

        function initializeModals() {
            var countDownInterval;
            $('#finish-btn').on('click', function() {
                ## Start countdown
                var countDown = 90;
                printCountDown();
                countDownInterval = window.setInterval(function() {
                    countDown--;
                    printCountDown();
                }, 1000);

                function printCountDown() {
                    if (countDown > 0) {
                        ## Show "please wait for <N> secs" message
                        $('#count-down-text').show();
                        $('#be-patient-text').hide();
                        $('#count-down-number').text(countDown);
                    } else {
                        ## Show "it should be done shortly" message
                        $('#count-down-text').hide();
                        $('#be-patient-text').show();
                    }
                }
            });
            $('#${progress_modal.id()}').on('hidden.bs.modal', function() {
                ## Stop countdown
                window.clearInterval(countDownInterval);
            });
        }

        function apply() {
            ${common.trackInitialTrialSetup('Clicked Apply Button')}

            ## Show the progress modal
            $('#${progress_modal.id()}').modal('show');

            reboot('default', waitForServicesReady, onFailure);
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
                                showErrorMessage('Service ' + service + ' failed to start.');
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
                $.post("${request.route_path('json-repackaging')}")
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
                        showErrorMessage("Repackaging of AeroFS clients couldn't complete.");
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
            $.post("${request.route_path('json-set-configuration-initialized')}")
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
            ## ask, why not use is_configuration_initialized() to disable data
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
