<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="spinner" file="../spinner.mako"/>
<%namespace name="common" file="common.mako"/>

<h4>Sit Back and Enjoy the Ride</h4>

## N.B. When adding or removing content, adjust the modals' "top" style
## to match the content's position.

<p>Your changes will be propagated to various AeroFS system components.
    This might take a short while.</p>

<form id="applyForm">
    ${csrf.token_input()}
    <hr/>
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
        Please wait for about 30 seconds.
        Grab a Philz coffee and relax...
    </div>
</div>

<div id="success-modal" class="modal hide" tabindex="-1" role="dialog"
        style="top: 150px; width: 440px; margin-left: -220px;">
    <div class="modal-header">
        <h4 class="text-success">System is ready!</h4>
    </div>
    <div class="modal-body">
        <p>Hooray! System configuration is complete.
            You will be redirected to the login page.</p>
    </div>
    <div class="modal-footer">
        <a href="#" class="btn btn-primary"
           onclick="redirectToHome(); return false;">Let's Roll</a>
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

        $progressModal.modal({
            ## See above
            backdrop: 'static',
            keyboard: false,
            show: false
        }).on('shown', function() {
            startSpinner($spinner, 0);
        }).on('hidden', function() {
            stopSpinner($spinner);
        });
    }

    var serializedData;
    function submitApplyForm() {
        lazyInitialize();

        ## Show the progress modal
        $('#progress-modal').modal('show');

        var $form = $('#applyForm');
        serializedData = $form.serialize();

        $(document).ready(function() {
            $("a").css("cursor", "arrow").click(false);
            $(":input").prop("disabled", true);
        });

        ## Stage 1: kick off the configuration process
        doPost("${request.route_path('json_config_apply')}",
                serializedData, pollForBootstrap, hideProgressModal);
    }

    ## Stage 2: wait until the configuration process is complete
    ## TODO (WW) add timeouts
    var interval;
    function pollForBootstrap() {
        interval = window.setInterval(function() {
            doPost("${request.route_path('json_config_poll')}",
                    serializedData, finalizeConfigurationIfCompleted);
        }, 1000);
    }

    ## Stage 3: ask the system to finalize the configuration
    function finalizeConfigurationIfCompleted(response) {
        if (response['completed'] == true) {
            window.clearInterval(interval);

            ## TODO (MP) not sure why the finalize call fails sometimes after
            ## the poller finishes.
            setTimeout(function() {
                doPost("${request.route_path('json_config_finalize')}",
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
