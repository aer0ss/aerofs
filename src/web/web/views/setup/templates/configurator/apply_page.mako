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
        Please wait for about 90 seconds.
        Grab a coffee and relax...
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

        ## Stage 1: kick off the configuration process
        doPost("${request.route_path('json_setup_apply')}",
                serializedData, pollForBootstrap, hideProgressModal);
    }

    ## Stage 2: wait until the configuration process is complete
    ## TODO (WW) add timeouts
    var interval;
    function pollForBootstrap() {
        interval = window.setInterval(function() {

            $.post("${request.route_path('json_setup_poll')}", serializedData)
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
            window.clearInterval(interval);

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
