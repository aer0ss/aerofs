<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="spinner" file="../spinner.mako"/>
<%namespace name="common" file="common.mako"/>

<h4>Sit back and relax</h4>

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

<div id="progress-modal" class="modal hide" tabindex="-1" role="dialog"
        style="top: 200px">
    <div class="modal-body">
        <span id="progress-modal-spinner" class="pull-left"
              style="margin-right: 28px; padding-top: -10px">&nbsp;</span>
        <span id="count-down-text">Please wait for about <strong><span id="count-down-number"></span></strong> seconds while the system is initializing...</span>
        <span id="be-patient-text">It should be done very shortly...</span>
    </div>
</div>

<div id="success-modal" class="modal hide" tabindex="-1" role="dialog"
        style="top: 150px; width: 440px; margin-left: -220px;">
    <div class="modal-header">
        <h4 class="text-success">Your system is ready!</h4>
    </div>
    <div class="modal-body">
        <p>Sweet! System configuration is complete.
            You will set up the first user account on the next page.</p>
    </div>
    <div class="modal-footer">
        <a href="#" class="btn btn-primary"
           onclick="redirectToHome(); return false;">Create First User</a>
    </div>
</div>

<%spinner:scripts/>

<script type="text/javascript">
    ## Because the special arrangement of configurator pages
    ## (see mode_supported_*.mako), inclusion of jQuery is after this block.
    ## Therefore, we can't intialize components at docuemnt loading time.
    var initialized;
    function lazyInitialize() {
        if (initialized) return;
        initialized = true;
        initializeSpinners();
        initializeModals();
    }

    function initializeModals() {
        var $progressModal = $('#progress-modal');
        var $successModal = $('#success-modal');
        var $spinner = $('#progress-modal-spinner');

        $successModal.modal({
            ## This is to prevent ESC or mouse clicking on the background to
            ## close the modal. See http://stackoverflow.com/questions/9894339/disallow-twitter-bootstrap-modal-window-from-closing
            backdrop: 'static',
            keyboard: false,
            show: false
        });

        var countDownInterval;
        $progressModal.modal({
            ## See above
            backdrop: 'static',
            keyboard: false,
            show: false
        }).on('shown', function() {
            startSpinner($spinner, 0);

            ## Sgtart countdown
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
            stopSpinner($spinner);
        });
    }

    function getSerializedFormData() {
        return $('#applyForm').serialize();
    }

    function submitApplyForm() {
        lazyInitialize();

        ## Show the progress modal
        $('#progress-modal').modal('show');

        ## Stage 1: kick off the configuration process
        doPost("${request.route_path('json_setup_apply')}",
                getSerializedFormData(), pollForBootstrap, hideProgressModal);
    }

    ## Stage 2: wait until the configuration process is complete
    ## TODO (WW) add timeouts
    function pollForBootstrap() {
        var bootstrapPollInterval = window.setInterval(function() {
            $.post("${request.route_path('json_setup_poll')}", getSerializedFormData())
            .done(function (response) {
                window.clearInterval(bootstrapPollInterval);
                finalizeConfigurationIfCompleted(response);
            }).fail(function (xhr) {
                ## TODO (MP) If 200 or 503 continue, otherwise bail. Need smarter integration with bootstrap here.
            });

        }, 1000);
    }

    ## Stage 3: ask the system to finalize the configuration
    function finalizeConfigurationIfCompleted(response) {
        if (response['completed'] == true) {
            setTimeout(function() {
                doPost("${request.route_path('json_setup_finalize')}",
                        serializedData, pollForRedirecting);
            }, 1000);
        } else {
            ## TODO (WW) add error handling, or do we need the "completed"
            ## flag at all?
        }
    }

    ## Stage 4: wait until the Web server is ready to serve requests
    ## TODO (WW) poll an actual URL, and add timetous
    function pollForRedirecting() {
        setTimeout(function() {
            hideProgressModal();
            $('#success-modal').modal('show');
        }, 3000);
    }

    ## TODO (WW) do not redirect to the home page for reconfigurations
    function redirectToHome() {
        ## TODO (MP) can improve this too, but for now leaving as-is,
        ## since this code will change in the next iteration anyway.
        window.location.href = "${request.route_path('marketing_home')}";
    }

    function hideProgressModal() {
        $('#progress-modal').modal('hide');
    }
</script>
