
<%def name='button(input_id, input_name)'>
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

<%def name="scripts(input_id, next_btn_id)">
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
                $('#${next_btn_id}').removeClass('disabled')
                    .addClass('btn-primary').focus();
            } else {
                $('#license-btn').removeClass('btn-success').addClass('btn-primary');
                $('#${next_btn_id}').removeClass('btn-primary')
                    .addClass('disabled');
            }
        }
    </script>
</%def>