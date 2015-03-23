<%namespace name="bootstrap" file="bootstrap.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Autocomplete Users" %>

<h2>
    Add Users to the Autocomplete Menu
</h2>

<div class="page-block">
    <p>
        The AeroFS Appliance normally shows users who have already signed up for AeroFS in autocomplete menus
        (such as the ones used for adding members to a shared folder). To make additional users show up in this
        menu, you can upload a file with other users' information here.
        <br><br>
        The file must be <strong>UTF-8 encoded</strong> where each user has one line containing:
        <div><code>email, first name, last name</code></div>
    </p>
</div>

<hr/>

<form role="form" method="post" onsubmit="return false;">
    ${csrf.token_input()}
    <div class="form-group">
        <input id="users-file" name="users-file" type="file" style="display: none" onchange="submitForm(this.form);">
        <button type="button" class="btn btn-primary" onclick="$('#users-file').click();">
                Upload Additional Users
        </button>
    </div>
</form>

<%block name="scripts">
    ## spinner support is required by progress_modal
    <%progress_modal:scripts/>
    <%spinner:scripts/>
    <%bootstrap:scripts/>

    <script>
        function submitForm(form) {
            console.log("upload backup file");
            var $progress = $('#${progress_modal.id()}');
            $progress.modal('show');

            $.ajax({
                url: "${request.route_path('json_upload_additional_users')}",
                type: "POST",
                ## See http://digipiph.com/blog/submitting-multipartform-data-using-jquery-and-ajax
                data: new FormData(form),
                contentType: false,
                processData: false
            })
            .done(function() {
                showSuccessMessage("Successfully updated additional users");
                $progress.modal('hide');
            }).fail(function(xhr) {
                showErrorMessage('Could not update additional users, please make sure the file uploaded is a valid CSV');
                $progress.modal('hide');
            });
        }
    </script>
</%block>
