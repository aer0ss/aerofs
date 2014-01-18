<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="bootstrap" file="../bootstrap.mako"/>
<%namespace name="modal" file="../modal.mako"/>
<%namespace name="license_common" file="../license_common.mako"/>

<form method="post" onsubmit="submitForm(); return false;">
    ${csrf.token_input()}
    <h3>Sorry, your license has expired</h3>

    <p class="text-error"><strong>
        Your license expired on ${current_config['license_valid_until']}.
        Please upload a new license file to proceed.
        <a href="mailto:support@aerofs.com">Contact us</a> to renew your
        license.
    </strong></p>

    ${license_common.big_upload_button('license-file', '')}

    <p class="text-right muted">
        In order for the new license to take effect, please click through<br>
        to the last step, and click 'Apply and Finish'.
    </p>

    <hr />
    <button class="btn pull-right" id="continue-btn" type="submit">Continue</button>
</form>

<%def name="scripts()">
    ${license_common.big_upload_button_script('license-file', 'continue-btn')}
    ${license_common.submit_scripts('license-file')}
</%def>