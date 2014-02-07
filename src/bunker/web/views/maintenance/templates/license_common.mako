
<%def name='big_upload_button(input_id, input_name)'>
    <input id="${input_id}" name="${input_name}" type="file" style="display: none">
    <div class="row-fluid" style="margin-top: 80px; margin-bottom: 50px;">
        <div class="span6 offset3">
            <p>
                <a href='#' id='license-btn' class="btn btn-large input-block-level"
                        onclick="$('#${input_id}').click(); return false;">
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

<%def name="big_upload_button_script(input_id, submit_btn_id)">
    <script>
        $(document).ready(function() {
            updateLicenseFileUI();

            $('#${input_id}').change(updateLicenseFileUI);
        });

        function updateLicenseFileUI() {
            var filename = $('#${input_id}').val();
            var hasFile = filename != "";
            $('.no-license').toggle(!hasFile);
            $('.has-license').toggle(hasFile);
            if (filename) {
                $('#license-btn').removeClass('btn-primary').addClass('btn-success');
                ## "C:\\fakepath\\" is a weirdo from the browser standard.
                $('#license-filename').text(filename.replace("C:\\fakepath\\", ''));
                setEnabled($('#${submit_btn_id}'), true)
                    .addClass('btn-primary').focus();
            } else {
                $('#license-btn').removeClass('btn-success').addClass('btn-primary');
                setEnabled($('#${submit_btn_id}'), false)
                    .removeClass('btn-primary');
            }
        }
    </script>
</%def>

<%def name="submit_scripts(license_file_input_id)">
    <script>
        ## @param postLicenseUpload a callback function after the license file
        ##  is uploaded. May be null. Expected signature:
        ##      postLicenseUpload(onSuccess, onFailure).
        function submitForm(postLicenseUpload) {
            ## Go to the next page if no license file is specified. This is
            ## needed for license_valid_page.mako to skip license upload if
            ## the license already exists.
            if (!$('#${license_file_input_id}').val()) gotoNextPage();

            disableNavButtons();

            ## TODO (WW) use multipart/form-data as in login.mako
            var file = document.getElementById('${license_file_input_id}').files[0];
            var reader = new FileReader();
            reader.onload = function() {
                submitLicenseFile(this.result, postLicenseUpload);
            };
            reader.readAsBinaryString(file);
        }

        function submitLicenseFile(license, postLicenseUpload) {
            var next;
            if (postLicenseUpload) {
                next = function() {
                    postLicenseUpload(gotoNextPage, enableNavButtons);
                };
            } else {
                next = gotoNextPage;
            }

            doPost("${request.route_path('json_set_license')}", {
                'license': license
            }, next, enableNavButtons);
        }
    </script>
</%def>
