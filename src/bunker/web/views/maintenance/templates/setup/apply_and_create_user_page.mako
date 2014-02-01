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
            <a id="dashboard_home_link" class="btn btn-primary">Close</a>
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
        <a id="org_users_link" class="btn btn-danger">
            Yes. Proceed</a>
    </div>
</div>

<%def name="scripts()">
    <script src="${request.static_path('web:static/js/purl.js')}"></script>
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
            populateDashboardLinks();
        });

        function populateDashboardLinks() {
            var homeBase = 'https://' + $.url().attr('host');
            $('#dashboard_home_link').attr('href', homeBase);
            $('#org_users_link').attr('href', homeBase + '/users');
        }

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

        function createUser() {
            setEnabled($('#create-user-btn'), false);
            ## See index.mako on why this request uses GET.
            ## TODO (WW) fixme
            ## $.get("${request.route_path('json.request_to_signup')}",
            $.get("///",
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
