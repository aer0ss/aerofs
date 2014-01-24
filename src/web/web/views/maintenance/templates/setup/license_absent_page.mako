<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="bootstrap" file="../bootstrap.mako"/>
<%namespace name="modal" file="../modal.mako"/>
<%namespace name="spinner" file="../spinner.mako"/>
<%namespace name="progress_modal" file="../progress_modal.mako"/>
<%namespace name="license_common" file="../license_common.mako"/>

<form method="post" onsubmit="$('#confirm-firewall-modal').modal('show'); return false;">
    ${csrf.token_input()}
    <h3>Welcome!</h3>

    <p>To setup this AeroFS Appliance, please upload your license to begin.</p>

    ${license_common.big_upload_button('license-file', '')}

    <p class="footnote">You should have received
        a license file from AeroFS along with this appliance. If not,
        please <a href="mailto:support@aerofs.com">contact us</a> to request
        a license.</p>

    <p class="footnote"><a target="_blank" href="https://support.aerofs.com/entries/25408319-What-happens-if-my-Private-Cloud-license-expires-">
    What happens if the license expires?</a></p>

    <hr />

    <div class="form-inline">
        <input id="backup-file" name="backup-file" type="file" style="display: none">

        <label class="checkbox">
            <input type="checkbox" id="backup-file-check" /> Restore
                <span id="backup-file-name"></span>
        </label>
        <button class="btn" id="backup-file-change"
                onclick="$('#backup-file').click(); return false;">Change</button>
        <button class="btn pull-right" id="continue-btn" type="submit">Continue</button>
    </div>

    <div class="form-inline" style="margin-top: 10px">
        <label class="checkbox">
            <input type="checkbox" id="data-collection" checked />
            Allow AeroFS to collect setup experience for trial users.
            <a href="https://support.aerofs.com/entries/25712809-Setup-experience-data-collection-for-Private-Cloud-trial-users"
               target="_blank">Read more.</a>
        </label>
    </div>
</form>

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
        <a class="btn btn-primary"
            onclick="$('#confirm-firewall-modal').modal('hide'); submitForm(preSumbit); return false;">
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
    ${license_common.big_upload_button_script('license-file', 'continue-btn')}
    ${license_common.submit_scripts('license-file')}

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

        function preSumbit(onSuccess, onFailure) {
            ## hide the firewall modal if open.
            ## TODO (WW) regiter a global handler to close all the modals
            ## before opening a new one. Also close error and success messages.
            $('#confirm-firewall-modal').modal('hide');

            setDataCollection(function() {
                restoreFromBackup(onSuccess, onFailure);
            }, onFailure);
        }

        function setDataCollection(onSuccess, onFailure) {
            var enable = $('#data-collection').is(':checked');
            console.log("set data collection: " + enable);

            $.post("${request.route_path('json_setup_set_data_collection')}", {
                enable: enable
            }).done(onSuccess).fail(function(xhr) {
                showAndTrackErrorMessageFromResponse(xhr);
                onFailure();
            });
        }

        function restoreFromBackup(onSuccess, onFailure) {

            ## Skip the step if the backup file is not specified
            if (!$('#backup-file').val()) {
                console.log("no backup specified. skip");
                onSuccess();
                return;
            }

            console.log("restore from backup");

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
                showAndTrackErrorMessageFromResponse(xhr);
                $progress.modal('hide');
                onFailure();
            });
        }
    </script>
</%def>