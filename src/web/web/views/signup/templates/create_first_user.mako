<%inherit file="marketing_layout.mako"/>
<%! page_title = "Create First Account" %>

<div class="row">
    <div class="col-sm-6 col-sm-offset-3">
        <h1 class="page-block">Create Admin Account</h1>
        <p class="page-block">Create the first admin account. Once finished, you can add more users
            using this account.</p>
        <p class="page-block">
            <form class="form-inline" onsubmit="submitForm(); return false;" role="form">
                <div class="form-group">
                    <label for="create-user-email" class="control-label">Email address:</label>
                    <input id="create-user-email" class="form-control" name="${url_param_email}" type="text">
                    <button id="create-user-btn" class="btn btn-primary">Continue</button>
                </div>
            </form>
        </p>
    </div>
</div>

<div id="email-sent-modal" class="modal" tabindex="-1" role="dialog">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h4>Please check your email</h4>
            </div>
            <div class="modal-body">
                <p>A confirmation email has been sent to <strong id="email-sent-address"></strong>.
                    Follow the instructions in the email to finish signing up the account.</p>
                <p>Check your junk mail folder or try again if you don't see this email.</p>
            </div>
            <div class="modal-footer">
                <a href="#" class="btn btn-primary" data-dismiss="modal">Close</a>
            </div>
        </div>
    </div>
</div>

<%block name="scripts">
    <script src="${request.static_path('web:static/js/purl.js')}"></script>
    <script>
        $(document).ready(function() {
            var $input = $('#create-user-email');
            ## This param is set by bunker's apply_page.mako
            $input.val($.url().param('email'))
                    .select()
                    .focus();
        });

        function submitForm() {
            var $btn = $('#create-user-btn');
            setEnabled($btn, false);
            $.get("${request.route_path('json.request_to_signup')}",
                $('form').serialize())
            .always(function() {
                setEnabled($btn, true);
            }).done(function() {
                hideAllModals();
                $('#email-sent-modal').modal('show');
                $('#email-sent-address').text($('#create-user-email').val());
            }).fail(showErrorMessageFromResponse);
        }
    </script>
</%block>
