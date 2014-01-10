## This page is used when the system's license is either absent or expired.
## In this case, uploading a new license does not require authentication.
## See docs/design/pyramid_auth.md, setup() in setup.py, and setup.mako.

<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="bootstrap" file="../bootstrap.mako"/>
<%namespace name="modal" file="../modal.mako"/>
<%namespace name="spinner" file="../spinner.mako"/>
<%namespace name="progress_modal" file="../progress_modal.mako"/>
<%namespace name="upload_license_button" file="../upload_license_button.mako"/>
<%namespace name="common" file="common.mako"/>

## The license exists but expired
%if is_license_present:
    <form method="post" onsubmit="submitForm(); return false;">
        ${csrf.token_input()}
        <h3>Sorry, your license has expired</h3>

        <p class="text-error"><strong>
            Your license expired on ${current_config['license_valid_until']}.
            Please upload a new license file to proceed.
            <a href="mailto:support@aerofs.com">Contact us</a> to renew your
            license.
        </strong></p>

        ${upload_license_button.button('license-file', '')}

        <p class="text-right muted">
            In order for the new license to take effect, please click through<br>
            to the last step, and click 'Apply and Finish'.
        </p>

        <hr />
        ${continue_button()}
    </form>

## The license doesn't exist (i.e. initial setup)
%else:
    <form method="post" onsubmit="$('#confirm-firewall-modal').modal('show'); return false;">
        ${csrf.token_input()}
        <h3>Welcome!</h3>

        <p>To setup this AeroFS Appliance, please upload your license to begin.</p>

        ${upload_license_button.button('license-file', '')}

        <p class="muted"><small>You should have received
            a license file from AeroFS along with this appliance. If not,
            please <a href="mailto:support@aerofs.com">contact us</a> to request
            a license. <a target="_blank" href="https://support.aerofs.com/entries/25408319-What-happens-if-my-Private-Cloud-license-expires-">
        What happens if the license expires?</a></small></p>

        <hr />

        ## It is a brand new install. Allow restoration from backup.
        <div class="form-inline">
            <input id="backup-file" name="backup-file" type="file" style="display: none">

            <label class="checkbox">
                <input type="checkbox" id="backup-file-check"> Restore
                    <span id="backup-file-name"></span>
            </label>
            <button class="btn" id="backup-file-change"
                    onclick="$('#backup-file').click(); return false;">Change</button>
            ${continue_button()}
        </div>
    </form>
%endif

<%def name="continue_button()">
    <button class="btn pull-right" id="continue-btn" type="submit">Continue</button>
</%def>

<%modal:modal>
    <%def name="id()">confirm-firewall-modal</%def>
    <%def name="title()">Firewall rules</%def>

    <p>The following ports need to be open for AeroFS desktop clients and Team
        Servers to connect to the appliance:</p>
    <ul>
        <li>TCP ports: 80, 443, 3478, 4433, 5222, 8084, 8888, and 29438.</li>
        <li>UDP port: 3478.</li>
    </ul>

    <p>Your firewall or VPN may require configuration to unblock these ports for
        your AeroFS clients. Please check this now.</p>
    <p><a target="_blank"
        href="https://support.aerofs.com/entries/22661589-Things-to-know-before-deploying-AeroFS-Private-Cloud">
        Read more about network requirements</a>.</p>

    <%def name="footer()">
        <a class="btn" href="#" data-dismiss="modal">Cancel</a>
        <a class="btn btn-primary" onclick="submitForm(restore); return false;">
            I've unblocked the ports. Continue</a>
    </%def>
</%modal:modal>

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

<%def name="scripts()">
    ## spinner support is required by progress_modal
    <%progress_modal:scripts/>
    <%spinner:scripts/>
    <%bootstrap:scripts/>
    ${upload_license_button.scripts('license-file', 'continue-btn')}
    ${submit_scripts('license-file')}

    ## Enable the restore-from-backup function if it's a initial setup.
    %if not is_license_present:
        <script>
            $(document).ready(function() {
                disableEsapingFromModal($('div.modal'));

                initializeProgressModal();

                updateBackupFileUI();
                $('#backup-file').change(updateBackupFileUI);

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
                ## Skip the step if the backup control is not present or the file is not
                ## specified
                if (!$('#backup-file').val()) {
                    onSuccess();
                    return;
                }

                var $progress = $('#${progress_modal.id()}');
                $progress.modal('show');

                ## See http://digipiph.com/blog/submitting-multipartform-data-using-jquery-and-ajax
                var formData = new FormData($('form')[0]);
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
    %endif
</%def>

<%def name="submit_scripts(license_file_input_id)">
    <script>
        ## @param postLicenseUpload a callback function after the license file
        ##  is uploaded. May be null. Expected signature:
        ##      postLicenseUpload(onSuccess, onFailure).
        function submitForm(postLicenseUpload) {
            ## Go to the next page if no license file is specified. This is
            ## needed for license_authorized_page.mako to skip license upload if
            ## the license already exists.
            if (!$('#${license_file_input_id}').val()) gotoNextPage();

            disableNavButtons();

            ## TODO (WW) use multipart/form-data as in maintenance_login.mako
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
