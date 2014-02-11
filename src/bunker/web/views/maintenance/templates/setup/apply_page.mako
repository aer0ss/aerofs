<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="bootstrap" file="../bootstrap.mako"/>
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

<div id="success-modal" class="modal hide small-modal" tabindex="-1" role="dialog">
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

<%def name="scripts()">
    <%progress_modal:scripts/>
    ## spinner support is required by progress_modal
    <%spinner:scripts/>
    <%bootstrap:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
            ## Disalbe esaping from all modals
            disableEsapingFromModal($('div.modal'));
            initializeModals();
        });

        function initializeModals() {
            var countDownInterval;
            $('#${progress_modal.id()}').on('shown', function() {
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
            }).on('hidden', function() {
                ## Stop countdown
                window.clearInterval(countDownInterval);
            });
        }

        function apply() {
            ${common.trackInitialTrialSetup('Clicked Apply Button')}

            ## Show the progress modal
            $('#${progress_modal.id()}').modal('show');

            runBootstrapTask('apply-config', finalize, function() {
                ## An error message is already shown by runBootstrapTask()
                hideAllModals();
                trackError();
            });
        }

        function finalize() {
            console.log("finalizing...");
            $.post("${request.route_path('json_setup_finalize')}")
            .done(function() {
                hideAllModals();
                $('#success-modal').modal('show');
                trackSuccessAndDisableDataCollection();
            }).fail(function(xhr) {
                hideAllModals();
                showAndTrackErrorMessageFromResponse(xhr);
            });
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
            $.post("${request.route_path('json_setup_set_data_collection')}", {
                enable: false
            });
        }
    </script>
</%def>
