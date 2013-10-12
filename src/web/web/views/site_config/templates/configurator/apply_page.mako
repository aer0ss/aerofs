<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

<h5>Apply Changes</h5>

<p>When you apply your changes, they will be propagated to various AeroFS system components. You will be redirected automatically once this operation has completed. This operation might take a few seconds.</p>

<form id="applyForm">
    ${csrf.token_input()}

    <hr/>

    <table width="100%" align="left|right">
    <tr>
    <td>${common.render_previous_button(page)}</td>
    <td align="right">
        <button
            onclick='return submitApplyForm();'
            id='nextButton'
            class='btn btn-primary'
            type='submit'>Apply and Finish</button>
    </td>
    </tr>
    </table>
</form>

<script type="text/javascript">

    var applySerializedData;

    function submitApplyForm()
    {
        showSuccessMessage("Please wait while your change is applied." +
                " You will be redirected automatically.");

        var $form = $('#applyForm');
        applySerializedData = $form.serialize();

        disableButtons();
        $(document).ready(function(){
            $("a").css("cursor", "arrow").click(false);
            $(":input").prop("disabled", true);
        });

        doPost("${request.route_path('json_config_apply')}",
                applySerializedData, initializeBootstrapPoller);
    }

    var interval;
    var firstRun = true;
    function initializeBootstrapPoller()
    {
        interval = self.setInterval(function() {doBootstrapPoll()}, 1000);
    }

    function doBootstrapPoll()
    {
        if (firstRun) {
            firstRun = false;
        } else {
            doPost("${request.route_path('json_config_poll')}",
                    applySerializedData, finalizeConfigurationSetupIfCompleted);
        }
    }

    function finalizeConfigurationSetupIfCompleted(response)
    {
        if (response['completed'] == true) {
            window.clearInterval(interval);

            ## TODO (MP) not sure why the finalize call fails sometimes after
            ## the poller finishes.
            setTimeout(function()
            {
                doPost("${request.route_path('json_config_finalize')}",
                        applySerializedData, redirectToHome);
            }, 1000);
        }
    }

    function redirectToHome()
    {
        ## TODO (MP) Add a better success message here. Perhaps integrate with spinner.
        ## Wait for the uwsgi reload to finish then redirect to home.
        setTimeout(function() {
            alert("Configured successfully. You will be redirected to the login page.");
            ## TODO (MP) can improve this too, but for now leaving as-is,
            ## since this code will change in the next iteration anyway.
            window.location.href = "${request.route_path('marketing_home')}";
        }, 3000);
    }
</script>
