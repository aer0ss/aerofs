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
    <p><span id="count-down-text">Please wait for about
        <strong><span id="count-down-number"></span></strong>
        seconds while the system is being configured...</span>
        <span id="be-patient-text">It should be done very shortly...</span>
    </p>
    ## Don't use <p> to wrap the following line to avoid an ugly, big padding
    ## between the line and the bottom of the modal.
    Once configuration finishes, your browser will automatically refresh.
</%progress_modal:html>

## TODO (WW) use progress_modal?
<div id="finalizing-modal" class="modal hide" tabindex="-1" role="dialog"
        style="top: 200px">
    <div class="modal-body">
        Finalizing configuration...
    </div>
</div>

<div id="success-modal" class="modal hide small-modal" tabindex="-1" role="dialog">
    <div class="modal-header">
        <h4 class="text-success">The system is ready!</h4>
    </div>
    <div class="modal-body">
        <p>System configuration is complete.</p>

        <% first_user_created = is_configuration_initialized or restored_from_backup %>

        %if first_user_created:
            ## It's a reconfiguration. Do nothing. Write something here otherwise
            ## the renderer would complain.
            <!---->
        %elif current_config['lib.authenticator'] == 'local_credential':
            ## It's an initial setup with local authentication
            <p>Next, you will create the system's first user.</p>
        %else:
            ## It's an initial setup with LDAP or OpenID
            <p>Next, you will create your first administrator account.
               All subsequent users will be regular users until promoted by an
               administrator.</p>
        %endif

    </div>
    <div class="modal-footer">
        %if first_user_created:
            <a href="${request.route_path('dashboard_home')}" class="btn btn-primary">Close</a>
        %elif current_config['lib.authenticator'] == 'local_credential':
            <a href="#" class="btn btn-primary" id="start-create-user-btn"
                onclick="hideAllModals(); $('#create-user-modal').modal('show'); return false;">
                Create First User</a>
        %else:
            <a href="${request.route_path('login')}" class="btn btn-primary">
                Login to Become First Admin</a>
        %endif
    </div>
</div>

<div id="create-user-modal" class="modal hide small-modal" tabindex="-1" role="dialog">
    <div class="modal-header">
        <h4>Create the first user</h4>
    </div>

    ## See index.mako on why this form uses GET
    <div class="modal-body">
        <p>Enter the email of the first user below. This user will become an
            administrator of your AeroFS Private Cloud.</p>
        <form id="create-user-form" method="get" class="form-inline">
            <label for="create-user-email">Email address:</label>
            <input id="create-user-email" name="${url_param_email}" type="text">
        </form>
    </div>
    <div class="modal-footer">
        <a href="#" id="create-user-btn" class="btn btn-primary"
           onclick="createUser(); return false;">Continue</a>
    </div>
</div>

<div id="email-sent-modal" class="modal hide small-modal" tabindex="-1" role="dialog">
    <div class="modal-header">
        <h4>Please check email</h4>
    </div>
    <div class="modal-body">
        <p>A confirmation email has been sent to:</p>
        <p class="text-center"><strong id="email-sent-address"></strong></p>
        <p>Follow the instructions in the email to finish signing up the account.
            Once finished, you can add more users with this account.</p>
    </div>
    <div class="modal-footer">
        <a href="#" class="btn"
            onclick="hideAllModals(); $('#create-user-modal').modal('show'); return false;">
            Resend Email</a>
        <a href="#" class="btn btn-primary"
            onclick="hideAllModals(); $('#confirm-add-user-modal').modal('show'); return false;">
            Finish Setup</a>
    </div>
</div>

<div id="confirm-add-user-modal" class="modal hide small-modal" tabindex="-1" role="dialog">
    <div class="modal-header">
        <h4>Did you finish signing up the first user?</h4>
    </div>
    <div class="modal-body">
        <p>Please make sure the first user is fully signed up before proceeding.
            Do not proceed if you haven't received the confirmation email.</p>
        <p>Have you received the email to sign up the first user?</p>
    </div>
    <div class="modal-footer">
        <a href="#" class="btn"
            onclick="hideAllModals(); $('#create-user-modal').modal('show'); return false;">
            No. Resend Email</a>
        <a href="${request.route_path('org_users')}" class="btn btn-danger">
            Yes. Proceed</a>
    </div>
</div>

<%def name="scripts()">
    <%progress_modal:scripts/>
    ## spinner support is required by progress_modal
    <%spinner:scripts/>
    <%bootstrap:scripts/>

    <script>
        var andFinalizeParam = '&finalize=1';

        $(document).ready(function() {
            initializeProgressModal();
            ## Disalbe esaping from all modals
            disableEsapingFromModal($('div.modal'));
            initializeModals();

            if (window.location.search.indexOf(andFinalizeParam) != -1) {
                ${common.trackInitialTrialSetup('Last Page Reloaded')}
                $('#finalizing-modal').modal('show');
                finalize();
            }
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

            $('#create-user-modal').on('shown', function () {
                $('#create-user-email').focus();
            });

            $('#create-user-form').submit(function() {
                createUser();
                return false;
            });
        }

        ########
        ## There are three steps to setup the system:
        ##  1. Kick-off the bootstrap process
        ##  2. Wait by polling until bootstrap is done
        ##  3. Set configuration_initialized to true (needed for initial setup)
        ## See step sepcific comments for details.

        ########
        ## Step 1: kick off the configuration process.

        function apply() {
            ${common.trackInitialTrialSetup('Clicked Apply Button')}

            ## Show the progress modal
            $('#${progress_modal.id()}').modal('show');

            var onFailure = hideAllModals;
            $.get('${request.route_path('json_get_license_shasum_from_session')}')
            .done(function(response) {
                var poll = function(eid) {
                    pollBootstrap(eid, response['shasum']);
                };
                enqueueBootstrapTask('apply-config', poll, onFailure);
            }).fail(onFailure);
        }

        ########
        ## Step 2: wait until the bootstrap process is complete. This step is tricky
        ## because of two issues:
        ##
        ## Issue 1: certificate change in the middle of bootstrap
        ## ======
        ##
        ## the user may uploads a new browser certificates, and part of bootstrap is
        ## to restart nginx which is a front-end of the uwsgi server. On restart,
        ## nginx will serve web requests with the new browser cert. This may cause
        ## subsequent AJAX calls (including the call in finalize() below) to fail
        ## if:
        ##
        ##  1. the new browser cert provided by the user is self-signed, or
        ##  2. the cert's cname doesn't match the current page's hostname, which is
        ##     the case at least for initial setup where the IP address is
        ##     used intead of the hostname.
        ##
        ## In both cases, the browser will block AJAX calls, causing $.post() to
        ## fail with a status code 0. The trick we employ to overcome this issue is
        ## to reload the current page with the new hostname before proceeding to the
        ## next step. The reloads allows the browser to 1) warn the user about a
        ## self-signed cert, and thus gives the user an opportunity to whitelist the
        ## cert for the browser, 2) use the hostname to match the new cert.
        ##
        ## See reloadToFinalize() below for the implementation of the trick.
        ##
        ## Issue 2: determinng bootstrap completion
        ## ======
        ##
        ## We rely on json_setup_poll() to tell whether the bootstrap process is
        ## complete. However, once nginx reloads the certificate, the AJAX call may
        ## fail with status code 0 until we reload the page (see issue 1).
        ##
        ## To workaround, we place nginx restart as the last step of the bootstrap
        ## process (see manual.tasks), and if we receive status code 0 consecutively
        ## several times, then we assume the bootstrap is done and it's time to
        ## reload the page. (The call may occasionally fail with code 0 due to
        ## restarts of other services.) If nginx restart doesn't cause AJAX calls to
        ## fail, we use normal returns from json_setup_poll() to determine
        ## completion.
        ##
        ## TODO (WW) a better alternative?

        function pollBootstrap(eid, licenseShasum) {
            ## the number of consecutive status-code-0 responses. See comments above
            var statusZeroCount = 0;
            var bootstrapPollInterval = window.setInterval(function() {
                $.get('${request.route_path('json_get_bootstrap_task_status')}', {
                    execution_id: eid
                }).done(function (response) {
                    statusZeroCount = 0;
                    if (response['status'] == 'success') {
                        console.log("poll complete");
                        window.clearInterval(bootstrapPollInterval);
                        reloadToFinalize(licenseShasum);
                    } else {
                        console.log("poll incomplete");
                        ## TODO (WW) add timeout?
                    }
                }).fail(function (xhr) {
                    console.log("status: " + xhr.status + " statusText: " + xhr.statusText);
                    if (xhr.status == 0) {
                        if (statusZeroCount++ == 10) reloadToFinalize(licenseShasum);

                    ## 400: aerofs specific error code, 500: internal server errors.
                    ## Because we are restarting nginx, the browser may throw
                    ## arbitrary codes. It's not safe to catch all error conditions.
                    } else if (xhr.status == 400 || xhr.status == 500) {
                        window.clearInterval(bootstrapPollInterval);
                        hideAllModals();
                        showAndTrackErrorMessageFromResponse(xhr);
                    }
                });

            }, 1000);
        }

        ## Reload the page and then call finalize() (see above comments). If the
        ## new hostname is different from the current hostname, we will lose the
        ## session cookie and no longer be able to call priviledged APIs.
        ## Therefore, we pass license_shasum to the new page which uses it to
        ## to perform license-shasum-based login.
        function reloadToFinalize(licenseShasum) {
            ## N.B. We expect a non-empty window.location.search here (i.e. '?page=X')
            ## Don't use "location.href =". It's not supported by old Firefox.
            window.location.assign('https://${current_config['base.host.unified']}' +
                    ## N.B. Can't use the 'setup' route here since it requires no
                    ## permission and thus bypasses license-based login in the
                    ## forbidden view.
                    '${request.route_path('setup_authorized')}' + window.location.search +
                    andFinalizeParam + "&${url_param_license_shasum}=" + licenseShasum);
        }

        ########
        ## Step 3: finalize the process after reloading the page.
        ##
        ## This step marks the system as initialized, so that browsing the AeroFS
        ## Web site will no longer be redirected to the setup page. This step can't
        ## be merged with step 2, because finalizing has to be done *after* boostrap
        ## completes. If in the middle of the bootstrap nginx reload causes AJAX
        ## calls to fail (see comments in step 2), we will not be able to call the
        ## server to finalize until the page is reloaded.

        function finalize() {
            doPost("${request.route_path('json_setup_finalize')}",
                { }, pollForWebServerReadiness);
        }

        ########
        ## Last, wait until uwsgi finishes reloading, which is caused by
        ## json_setup_finalize(). See that method for detail.
        function pollForWebServerReadiness() {
            var interval = window.setInterval(function() {
                $.get('${request.route_path('json_is_uwsgi_reloading')}')
                .done(function(resp) {
                    var reloading = resp['reloading'];
                    console.log("uwsgi reloading: " + reloading);

                    if (!reloading) {
                        console.log("uwsgi reloaded successfully");
                        window.clearInterval(interval);
                        hideAllModals();
                        $('#success-modal').modal('show');
                        trackSuccessAndDisableDataCollection();
                    } else {
                        console.log("uwsgi still reloading");
                        ## TODO (WW) add timeout?
                    }
                }).fail(function(xhr, textStatus, errorThrown) {
                    console.log("error from is_uwsgi_reloding(): " + xhr.status +
                            " " + xhr.readyState + " " + xhr.statusText +
                            " " + textStatus + " " + errorThrown);

                    ## Ignore 403 and 0's. We'll see this when uwsgi is reloading.
                    ## For more details see bootstrap.mako.
                    if (xhr.status != 403 && xhr.status != 0) {
                        window.clearInterval(interval);
                        hideAllModals();
                        showAndTrackErrorMessageFromResponse(xhr);
                    }
                });
            }, 1000);
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

        function createUser() {
            setEnabled($('#create-user-btn'), false);
            ## See index.mako on why this request uses GET.
            $.get("${request.route_path('json.request_to_signup')}",
                    $('#create-user-form').serialize())
            .always(function() {
                setEnabled($('#create-user-btn'), true);
            })
            .done(function() {
                hideAllModals();
                $('#email-sent-modal').modal('show');
                $('#email-sent-address').text($('#create-user-email').val());
            })
            .fail(showAndTrackErrorMessageFromResponse);
        }
    </script>
</%def>
