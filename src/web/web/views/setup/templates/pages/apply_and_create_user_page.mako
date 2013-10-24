<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

## N.B. When adding or removing content, adjust the modals' "top" style
## to match the content's position.

<p>Your changes will be applied to various AeroFS system components.
    This might take a short while.</p>
<hr />
<form id="applyForm" method="POST">
    ${csrf.token_input()}
    ${common.render_previous_button(page)}
    <button
        onclick='submitApplyForm(); return false;'
        id='nextButton'
        class='btn btn-primary pull-right'>Apply and Finish</button>
</form>

<%common:progress_modal_html>
    <span id="count-down-text">Please wait for about
        <strong><span id="count-down-number"></span></strong>
        seconds while the system is being configured...</span>
    <span id="be-patient-text">It should be done very shortly...</span>
</%common:progress_modal_html>

<div id="success-modal" class="modal hide small-modal" tabindex="-1" role="dialog">
    <div class="modal-header">
        <h4 class="text-success">The system is ready!</h4>
    </div>
    <div class="modal-body">
        <p>System configuration is complete.</p>

        %if is_configuration_initialized:
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
        %if is_configuration_initialized:
            <a href="${request.route_path('setup')}" class="btn btn-primary">Close</a>
        %elif current_config['lib.authenticator'] == 'local_credential':
            <a href="#" class="btn btn-primary"
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
            <label for="${url_param_email}">Email address:</label>
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
        <p>Follow the instructions in the email to finish signing up the user.
            Once finished, you can invite more users with this user account.</p>
    </div>
    <div class="modal-footer">
        <a href="#" class="btn"
            onclick="hideAllModals(); $('#create-user-modal').modal('show'); return false;">
            Resend Email</a>
        <a href="#" class="btn btn-primary"
            onclick="hideAllModals(); $('#confirm-add-user-modal').modal('show'); return false;">
            Add More Users</a>
    </div>
</div>

<div id="confirm-add-user-modal" class="modal hide small-modal" tabindex="-1" role="dialog">
    <div class="modal-header">
        <h4>Have you finished signing up the first user?</h4>
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
        <a href="${request.route_path('team_members')}" class="btn btn-danger">
            Yes. Proceed</a>
    </div>
</div>

<%common:progress_modal_scripts/>

<script>
    $(document).ready(function() {
        initializeProgressModal();
        ## Disalbe esaping from all modals
        disableEsapingFromModal($('div.modal'));
        initializeModals();
    });

    function initializeModals() {
        var countDownInterval;
        $('#${common.progress_modal_id()}').on('shown', function() {
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

    function getSerializedFormData() {
        return $('#applyForm').serialize();
    }

    function submitApplyForm() {
        ## Show the progress modal
        $('#progress-modal').modal('show');

        ## Stage 1: kick off the configuration process
        doPost("${request.route_path('json_setup_apply')}",
                getSerializedFormData(), pollForBootstrap, hideAllModals);
    }

    ## Stage 2: wait until the configuration process is complete
    ## TODO (WW) add timeouts
    var bootstrapPollInterval;
    function pollForBootstrap() {
        bootstrapPollInterval = window.setInterval(function() {
            $.post("${request.route_path('json_setup_poll')}", getSerializedFormData())
            .done(function (response) {
                finalizeConfigurationIfCompleted(response);
            }).fail(function (xhr) {
                ## TODO (MP) If 200 or 503 continue, otherwise bail. Need smarter integration with bootstrap here.
            });

        }, 1000);
    }

    ## Stage 3: ask the system to finalize the configuration
    function finalizeConfigurationIfCompleted(response) {
        if (response['completed'] == true) {
            window.clearInterval(bootstrapPollInterval);
            setTimeout(function() {
                doPost("${request.route_path('json_setup_finalize')}",
                        getSerializedFormData(), pollForWebServerReadiness);
            }, 1000);
        } else {
            ## TODO (WW) add error handling, or do we need the "completed"
            ## flag at all?
        }
    }

    ## Stage 4: wait until the Web server is ready to serve requests
    ## TODO (WW) poll an actual URL, and add timeouts
    function pollForWebServerReadiness() {
        setTimeout(function() {
            hideAllModals();
            $('#success-modal').modal('show');
        }, 3000);
    }

    function hideAllModals() {
        $('div.modal').modal('hide');
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
        .fail(showErrorMessageFromResponse);
    }
</script>
