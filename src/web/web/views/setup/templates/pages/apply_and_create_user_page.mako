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
        onclick='submitForm(); return false;'
        id='nextButton'
        class='btn btn-primary pull-right'>Apply and Finish</button>
</form>

<%common:progress_modal_html>
    <p><span id="count-down-text">Please wait for about
        <strong><span id="count-down-number"></span></strong>
        seconds while the system is being configured...</span>
        <span id="be-patient-text">It should be done very shortly...</span>
    </p>
    <p>
        Once configuration finishes, your browser will automatically refresh.
    </p>
</%common:progress_modal_html>

## TODO (WW) use progress_modal
<div id="finalizing-modal" class="modal hide" tabindex="-1" role="dialog"
        style="top: 200px">
    <div class="modal-body">
        Finalizing the configuration...
    </div>
</div>

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
    var andFinalizeParam = '&finalize=1';

    $(document).ready(function() {
        initializeProgressModal();
        ## Disalbe esaping from all modals
        disableEsapingFromModal($('div.modal'));
        initializeModals();

        if (window.location.search.indexOf(andFinalizeParam) != -1) {
            $('#finalizing-modal').modal('show');
            finalize();
        }
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

    ########
    ## There are three steps to setup the system:
    ##  1. Kick-off the bootstrap process
    ##  2. Wait by polling until bootstrap is done
    ##  3. Set configuration_initialized to true (needed for initial setup)
    ## See step sepcific comments for details.

    ########
    ## Step 1: kick off the configuration process

    function submitForm() {
        ## Show the progress modal
        $('#${common.progress_modal_id()}').modal('show');

        doPost("${request.route_path('json_setup_apply')}",
                getSerializedFormData(), pollForBootstrap, hideAllModals);
    }

    ########
    ## Step 2: wait until the configuration process is complete.
    ##
    ## This step is tricky because the user may uploads new browser certificates.

    function pollForBootstrap() {
        ## the number of consecutive 0-status responses
        var statusZeroCount = 0;
        var bootstrapPollInterval = window.setInterval(function() {
            $.post("${request.route_path('json_setup_poll')}", getSerializedFormData())
            .done(function (response) {
                statusZeroCount = 0;
                if (response['completed'] == true) {
                    console.log("poll complete");
                    window.clearInterval(bootstrapPollInterval);
                    reloadToFinalize();
                } else {
                    console.log("poll incomplete");
                    ## TODO (WW) add timeout?
                }
            }).fail(function (xhr) {
                console.log("status: " + xhr.status + " statusText: " + xhr.statusText);
                ## TODO (WW) add comments. nginx restart itself can cause zero status responses
                if (xhr.status == 0 && statusZeroCount++ == 10) {
                    reloadToFinalize();
                }
            });

        }, 1000);
    }

    ########
    ## Step 3: reload the page and finalize

    function reloadToFinalize() {
        ## We expect non-empty window.location.search here (i.e. '?page=X')
        window.location.href = 'https://${current_config['base.host.unified']}' +
                window.location.pathname + window.location.search + andFinalizeParam;
    }

    function finalize() {
        doPost("${request.route_path('json_setup_finalize')}",
                getSerializedFormData(), pollForWebServerReadiness);
    }

    ########
    ## Last, wait until uwsgi finishes reloading, which is caused by
    ## json_setup_finalize().

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
