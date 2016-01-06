<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="bootstrap" file="../bootstrap.mako"/>
<%namespace name="modal" file="../modal.mako"/>
<%namespace name="spinner" file="../spinner.mako"/>
<%namespace name="progress_modal" file="../progress_modal.mako"/>

<%block name="css">
    <style>
        .btn-large {
            ## keep the height consistent with login.mako
            height: 60px;
            margin-top: 40px;
        }
    </style>
</%block>

<form role="form" method="post" action="${request.route_path('setup_submit_data_collection_form')}">
    ${csrf.token_input()}
    <p>Please select your next step:</p>

    <div class="form-group">
        <button type="submit" class="form-control btn btn-large btn-primary ">
            Create a New Appliance
        </button>
    </div>
    <div class="form-group">
        <input id="backup-file" name="backup-file" type="file" style="display: none">
        <button type="button" id="restore-button" class="form-control btn btn-default btn-large "
                onclick="$('#backup-file').click(); return false;">
            Restore from Backup
        </button>
        <div class="help-block">
            Backup files are in the format of <em>${example_backup_download_file_name}</em>
        </div>
    </div>

    <hr />

    <div class="checkbox">
        <label>
            <input type="checkbox" id="data-collection" name="data-collection" checked />
            Allow AeroFS to collect setup experience for trial licenses.
            <a href="https://support.aerofs.com/hc/en-us/articles/201439624"
               target="_blank">Read more.</a>
        </label>
    </div>
</form>

<%progress_modal:progress_modal>
    <%def name="id()">create-modal</%def>
    <p>The system is restoring from the backup file...</p>
    Depending on the backup file size, this may take a while.
</%progress_modal:progress_modal>

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

    <script>
        $(document).ready(function() {
            disableEscapingFromModal($('div.modal'));
            initializeProgressModal();
            $('#backup-file').change(onBackupFileChange);
        });

        $('#create-modal').modal({
            backdrop: 'static',
            keyboard: false,
            show: false
        });

        function onBackupFileChange() {
            var hasFile = $('#backup-file').val() != "";
            ## Do nothing if the user doesn't provide a valid file.
            if (hasFile) uploadAndRestoreFromBackup();
        }

        function uploadAndRestoreFromBackup() {
            console.log("upload backup file");
            var $progress = $('#create-modal');
            $progress.modal('show');

            $.ajax({
                url: "${request.route_path('json_upload_backup')}",
                type: "POST",
                ## See http://digipiph.com/blog/submitting-multipartform-data-using-jquery-and-ajax
                data: new FormData($('form')[0]),
                contentType: false,
                processData: false
            })
            .done(restoreFromBackup)
            .fail(function(xhr) {
                showAndTrackErrorMessageFromResponse(xhr);
                $progress.modal('hide');
            });
        }

        function restoreFromBackup() {
            console.log("start restore");
            $.post("${request.route_path('json-restore')}")
                .done(waitForRestore)
                .fail(failed);
        }

        function waitForRestore() {
            console.log("wait for restore done");
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-restore')}")
                .done(function(resp) {
                    if (resp['running']) {
                        console.log('restore is running. wait');
                    } else if (resp['succeeded']) {
                        console.log('restore done');
                        window.clearInterval(interval);
                        setRestoredFlag();
                    } else {
                        console.log('restore failed');
                        window.clearInterval(interval);
                        $('#create-modal').modal('hide');
                        showLogPromptWithMessageUnsafe("Restore failed.");
                    }
                }).fail(failed);
            }, 1000);
        }

        function setRestoredFlag() {
            $.post('${request.route_path('json_setup_set_restored_from_backup')}')
                .done(restoreFromBackupDone)
                .fail(failed);
        }

        function restoreFromBackupDone() {
            $('#create-modal').modal('hide');
            $('#backup-success-modal').modal('show');
            $('#backup-success-ok').off().click(function() {
                ## Backup is done. Submit the form.
                ## Clear the backup-file field so it's not submitted as part of the form.
                $('#backup-file').val('');
                $('form').submit();
                return false;
            });
        }

        function failed(xhr) {
            showAndTrackErrorMessageFromResponse(xhr);
            $('#create-modal').modal('hide');
        }
    </script>
</%def>
