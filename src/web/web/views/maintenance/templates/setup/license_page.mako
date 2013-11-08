## This page is used when the system's license is either absent or expired.
## In this case, uploading a new license does not require authentication.
## See docs/design/site_setup_auth.md, setup() in setup.py, and setup.mako.

<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="bootstrap" file="../bootstrap.mako"/>
<%namespace name="modal" file="../modal.mako"/>
<%namespace name="spinner" file="../spinner.mako"/>
<%namespace name="progress_modal" file="../progress_modal.mako"/>
<%namespace name="upload_license_button" file="../upload_license_button.mako"/>
<%namespace name="common" file="common.mako"/>

<form method="post" id="license-form">
    %if is_license_present:
        <h3>Sorry, your license has expired</h3>

        <p class="text-error"><strong>
            Your license has expired on
            ${current_config['license_valid_until']}. Please upload a new
            license to proceed:
        </strong></p>

        ${upload_license_button.button('license-file', '')}

        <p class="text-right muted">
            In order for the new license to take effect, please click through<br>
            to the last step, and click 'Apply and Finish'.
        </p>
    %else:
        ## The license doesn't exist (i.e. initial setup)
        <h3>Welcome!</h3>

        <p>To setup this AeroFS Appliance, please upload your license to begin.</p>

        ${upload_license_button.button('license-file', '')}
    %endif
</form>


<hr />

%if not is_license_present:
    ## It is a brand new install. Allow restoration from backup.
    <form method="post" id="backup-file-form" class="form-inline">
        ${csrf.token_input()}
        <input id="backup-file" name="backup-file" type="file" style="display: none">

        <label class="checkbox">
            <input type="checkbox" id="backup-file-check"> Restore
                <span id="backup-file-name"></span>
        </label>
        <button class="btn" id="backup-file-change"
                onclick="$('#backup-file').click(); return false;">Change</button>
        ${common.render_next_button("submitForm(restore)")}
    </form>
%else:
    ${common.render_next_button("submitForm()")}
%endif

<%progress_modal:html>
    <p>Please wait while the system restores from the backup file...</p>
    Depending on the backup file size, this may take a while.
</%progress_modal:html>

<%modal:modal>
    <%def name="id()">backup-success-modal</%def>
    <%def name="title()">Please finish configuration</%def>
    The system is successfully restored. To complete the setup, please click
        through to the last step, and click <strong>Apply and Finish</strong>.
    <%def name="footer()">
        <button id="backup-success-ok" class="btn btn-primary">Continue</button>
    </%def>
</%modal:modal>

## spinner support is required by progress_modal
<%progress_modal:scripts/>
<%spinner:scripts/>
<%bootstrap:scripts/>
## N.B. 'next-btn' must be consistent with common.next_button_id().
## Due to limitation of mako we can't use common.next_button_id() directly here.
${upload_license_button.scripts('license-file', 'next-btn')}
${submit_scripts('license-file')}

<script>
    $(document).ready(function() {
        disableEsapingFromModal($('div.modal'));

        initializeProgressModal();

        updateBackupFileUI();

        $('#backup-file-check').click(function() {
            if ($(this).is(':checked')) {
                $('#backup-file').click();
                ## prevent default behavior. Don't change the checked state
                ## until the user chooses a file.
                return false;
            } else {
                $('#backup-file').val('');
                updateBackupFileUI();
                return true;
            }
        });

        $('#backup-file').change(updateBackupFileUI);
    });

    function updateBackupFileUI() {
        var filename = $('#backup-file').val();
        var hasFile = filename != "";

        var $checkbox = $('#backup-file-check');
        if (hasFile) $checkbox.attr("checked", "checked");
        else $checkbox.removeAttr("checked");
        $('#backup-file-change').toggle(hasFile);
        $('#backup-file-name').text(hasFile ?
                ## "C:\\fakepath\\" is a weirdo from the browser standard.
                'from ' + filename.replace("C:\\fakepath\\", '') :
                'an appliance from backup');
    }

    function restore(onSuccess, onFailure) {
        ## Skip the step if the backup file is not specified
        if (!$('#backup-file').val()) {
            onSuccess();
            return;
        }

        var $progress = $('#${progress_modal.id()}');
        $progress.modal('show');

        ## See http://digipiph.com/blog/submitting-multipartform-data-using-jquery-and-ajax
        var formData = new FormData($('#backup-file-form')[0]);
        $.ajax({
            url: "${request.route_path('json_upload_backup')}",
            type: "POST",
            data: formData,
            contentType: false,
            processData: false
        }).done(function() {
            runBootstrapTask('db-restore', function() {
                $progress.modal('hide');
                $('#backup-success-modal').modal('show');
                $('#backup-success-ok').click(function() {
                    onSuccess();
                    return false;
                });
            }, function() {
                $progress.modal('hide');
                onFailure();
            });
        }).fail(function(xhr) {
            showErrorMessageFromResponse(xhr);
            $progress.modal('hide');
            onFailure();
        });
    }
</script>

<%def name="submit_scripts(license_file_input_id)">
    <script>
        ## postLicenseUpload a callback function after the license file is uploaded.
        ## Required signature: post_license_file_upload(onSuccess, onFailure).
        ## May be null
        function submitForm(postLicenseUpload) {
            ## Go to the next page if no license file is specified. This is
            ## needed for license_authorized_page.mako to skip license upload if
            ## the license already exists.
            if (!$('#${license_file_input_id}').val()) gotoNextPage();

            disableNavButtons();
            ## TODO (WW) is there a clean way to submit the file data?
            ## Note: FileReader is supported in IE9.
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
                ${csrf.token_param()}
                'license': license
            }, next, enableNavButtons);
        }
    </script>
</%def>