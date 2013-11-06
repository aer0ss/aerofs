## This page is used when the system's license is either absent or expired.
## In this case, uploading a new license does not require authentication.
## See docs/design/site_setup_auth.md, setup() in setup.py, and setup.mako.

<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

<form method="post" id="license-form">
    %if is_license_present:
        <h3>Sorry, your license has expired</h3>

        <p class="text-error"><strong>
            Your license has expired on
            ${current_config['license_valid_until']}. Please upload a new
            license to proceed:
        </strong></p>

        <input id="license-file" type="file" style="display: none">
        ${upload_license_button()}

        <p class="text-right muted" style="margin-top: 80px;">
            In order for the new license to take effect, you must click through<br>
            to the last step, and click 'Apply and Finish'.
        </p>
    %else:
        ## The license doesn't exist (i.e. initial setup)
        <h3>Welcome!</h3>

        <p>You will set up the AeroFS Appliance in the next few pages.
            Please upload your license to begin:</p>

        <input id="license-file" type="file" style="display: none">
        ${upload_license_button()}
    %endif
</form>

<%def name='upload_license_button()'>
    <div class="row-fluid" style="margin-top: 80px;">
        <div class="span6 offset3">
            <p>
                <a href='#' id='license-btn' class="btn btn-large input-block-level"
                        onclick="$('#license-file').click(); return false;">
                    <span class="no-license">Upload License File</span>
                    <span class="has-license">License Ready to Upload</span>
                </a>
            </p>
            <p class="text-center">
                <span class="no-license">Your license file ends in <em>.license</em></span>
                <span class="has-license"><span id="license-filename"></span> selected for upload</span>
            </p>
        </div>
    </div>
</%def>

<hr />
${common.render_next_button("submitForm()")}

${submit_form_scripts('submitForm', 'license-file')}

<script>
    $(document).ready(function() {
        $('#license-file').on('change', function() {
            setLicenseFile($(this).val());
        });

        setLicenseFile(null);
    });

    ## @param filename null or empty if the file is not available
    function setLicenseFile(filename) {
        if (filename) {
            $('.no-license').hide();
            $('.has-license').show();
            $('#license-btn').removeClass('btn-primary').addClass('btn-success');
            $('#license-filename').text(filename.replace("C:\\fakepath\\", ''));
            $('#${common.next_button_id()}').removeClass('disabled')
                    .addClass('btn-primary').focus();
        } else {
            $('.no-license').show();
            $('.has-license').hide();
            $('#license-btn').removeClass('btn-success').addClass('btn-primary');
            $('#${common.next_button_id()}').removeClass('btn-primary')
                    .addClass('disabled');
        }
    }
</script>

<%def name="submit_form_scripts(function_name, elem_id)">
    <script>
        function ${function_name}() {
            ## Go to the next page directly if no license file is specified
            if (!$('#${elem_id}').val()) gotoNextPage();

            disableNavButtons();
            ## TODO (WW) is there a clean way to submit the file data?
            ## Note: FileReader is supported in IE9.
            var file = document.getElementById('${elem_id}').files[0];
            var reader = new FileReader();
            reader.onload = function() { submitLicenseFile(this.result); };
            reader.readAsBinaryString(file);
        }

        function submitLicenseFile(license) {
            $.post("${request.route_path('json_set_license')}", {
                ${csrf.token_param()}
                'license': license
            })
            .done(gotoNextPage)
            .fail(function (xhr) {
                enableNavButtons();
                showErrorMessageFromResponse(xhr);
            });
        }
    </script>
</%def>