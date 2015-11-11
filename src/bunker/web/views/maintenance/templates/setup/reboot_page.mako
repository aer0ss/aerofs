<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="loader" file="../loader.mako"/>
<%namespace name="common" file="setup_common.mako"/>
<%namespace name="spinner" file="../spinner.mako"/>
<%namespace name="progress_modal" file="../progress_modal.mako"/>

<h4>Reboot system</h4>

<p>Your changes will be applied to various AeroFS system components.
   This might take a short while. Please do not close this browser window.</p>

<form method="POST" role="form" onsubmit="submitForm(); return false;">
    ${common.render_next_prev_buttons()}
</form>

<%progress_modal:html>
    <span>Please wait while the system reboots...</span>
</%progress_modal:html>

<div id="success-modal" class="modal" tabindex="-1" role="dialog">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h4 class="text-success">Reboot complete</h4>
            </div>
            <div class="modal-body">
                Your appliance has rebooted.
            </div>
            <div class="modal-footer">
                <a class="btn btn-primary"
                    onclick="gotoNextPage(); return false;">Continue</a>
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

        function apply() {
            ${common.trackInitialTrialSetup('Clicked Apply Button')}
            $('#${progress_modal.id()}').modal('show');
            reboot('default', conclude, onFailure);
        }

        function conclude() {
            hideAllModals();
            $('#success-modal').modal('show');
        }

        function onFailure(xhr) {
            console.log('failed');
            hideAllModals();
            showAndTrackErrorMessageFromResponse(xhr);
        }

        function submitForm() {
            apply();
        }
    </script>
</%def>
