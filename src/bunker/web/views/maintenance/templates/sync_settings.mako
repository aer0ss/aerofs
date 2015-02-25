<%namespace name="bootstrap" file="bootstrap.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Sync Settings" %>

<h2>
    Sync Appliance Settings From Backup
</h2>

<div class="page-block">
    <p>
        Any settings changed through the AeroFS appliance management portal will not be changed in
        backup appliances. If you have changed any appliance settings since the modification time
        shown below, it is highly recommended to update appliance settings by uploading a current
        backup file below.
    </p>

    <p>
        %if modification_time:
            This appliance's last modification time is <code><span id="modification-time"></span></code>.
        %else:
            We have no record of this appliance's last modification time.
        %endif
    </p>
</div>

<hr/>

<form role="form" method="post" action="javascript:void(0);">
    ${csrf.token_input()}
    <div class="form-group">
        <input id="backup-file" name="backup-file" type="file" style="display: none">
        <button type="button" class="btn btn-primary"
                onclick="$('#backup-file').click(); return false;">
                Sync Settings
        </button>
        <div class="help-block">
            Backup files are in the format of <em>${example_backup_download_file_name}</em>
        </div>
    </div>
</form>

<%progress_modal:html>
    The system is syncing settings from the backup file...
</%progress_modal:html>

<%block name="scripts">
    ## spinner support is required by progress_modal
    <%progress_modal:scripts/>
    <%spinner:scripts/>
    <%bootstrap:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();
            populateModificationTime("${modification_time}");
            $('#backup-file').change(onBackupFileChange);
        });

        function populateModificationTime(isoformatUTC) {
            var modificationTimeDiv = $('#modification-time')[0];
            if (modificationTimeDiv) {
                var localTime = datetimeFromUTC(isoformatUTC);
                modificationTimeDiv.innerHTML = localTime.toLocaleTimeString() + " " + localTime.toLocaleDateString();
            }
        }

        function datetimeFromUTC(isoformatUTC) {
            var utcTime = new Date(isoformatUTC);
            return new Date(utcTime.getTime() + utcTime.getTimezoneOffset());
        }

        function onBackupFileChange() {
            var hasFile = $('#backup-file').val() != "";
            ## Do nothing if the user doesn't provide a valid file.
            if (hasFile) uploadAndRestoreFromBackup();
        }

        function uploadAndRestoreFromBackup() {
            console.log("upload backup file");
            var $progress = $('#${progress_modal.id()}');
            $progress.modal('show');

            $.ajax({
                url: "${request.route_path('json_upload_backup')}",
                type: "POST",
                ## See http://digipiph.com/blog/submitting-multipartform-data-using-jquery-and-ajax
                data: new FormData($('form')[0]),
                contentType: false,
                processData: false
            })
            .done(updateFromBackup)
            .fail(function(xhr) {
                showAndTrackErrorMessageFromResponse(xhr);
                $progress.modal('hide');
            });
        }

        function updateFromBackup() {
            var $progress = $('#${progress_modal.id()}');
            runBootstrapTask('update-properties-from-backup', function() {
                showSuccessMessage("Successfully updated properties");
                $progress.modal('hide');
            }, function() {
                $progress.modal('hide');
            });
        }

    </script>
</%block>
