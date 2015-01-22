<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Settings" %>

<%namespace name="modal" file="modal.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>
<%namespace name="spinner" file="spinner.mako"/>

<%! from datetime import date %>

<h2>Settings</h2>

<div class="row">
    <div class="col-sm-12">
        <h4>Change your name</h4>
        <form class="form-horizontal" role="form" id="fullname-form" method="post">
            ${self.csrf.token_input()}
            <fieldset>
                ## <legend>User info</legend>
                <div class="form-group">
                    <label class="col-sm-2 control-label" for="first-name">First name:</label>
                    <div class="col-sm-6">
                        <input type="text" class="form-control" id="first-name" name="first-name"
                                value="${first_name}">
                    </div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label" for="last-name">Last name:</label>
                    <div class="col-sm-6">
                        <input type="text"  class="form-control" id="last-name" name="last-name"
                                value="${last_name}">
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-sm-6 col-sm-offset-2">
                        <button class="btn btn-primary" class="form-control" id="fullname-btn">Update</button>
                    </div>
                </div>
            </fieldset>
        </form>
    </div>
</div>

%if can_has_tfa:
<div class="row">
    <hr>
    <div class="col-sm-12">
        <h4>Two-factor authentication</h4>
        % if two_factor_enforced:
        <p>Status: On |
        <a href="${request.route_path('two_factor_settings')}">Edit &raquo;</a></p>
        % else:
        <p>Status: Off |
        <a href="${request.route_path('two_factor_intro')}">Set up &raquo;</a></p>
        % endif
    </div>
</div>
%endif

<div class="row">
    <hr>
    <div class="col-sm-12">
        <h4>Administration</h4>
        <a href="#" id="password-reset">
            Reset your password
        </a>
        <br><br>
        <a href="#" id="delete-account">
            Delete your account
        </a>
    </div>
</div>

<div class="row">
    <hr>
    <p>
    <a id="show-advanced-settings-options" href="#"
            onclick="showAdvancedSettingsOptions(); return false;">
        Show advanced options &#x25BE;</a>
    <a id="hide-advanced-settings-options" href="#"
            onclick="hideAdvancedSettingsOptions(); return true;" style="display: none;">
        Hide advanced options &#x25B4;</a>
    </p>

    ## Currently we show advanced options if the user has settings token exists (to handle the
    ## case where the user creates a token and wants to see the page refresh correctly). Revisit
    ## this if adding more things to the advanced settings area.
    <div id="advanced-settings-options"
        % if len(user_settings_token) == 0:
            style="display: none;"
        % endif
        >
        ${advanced_settings_options()}
    </div>
</div>

<%def name="advanced_settings_options()">
    <div class="col-sm-12 footnote">
    <h4>API Access Tokens</h4>

    <p>
        You can use the AeroFS API to access AeroFS functionality, such as creating, editing, and
        deleting documents. You can integrate AeroFS features in your own applications or build apps
        to add functionality to AeroFS. Note that tokens in this context are granted all user, file,
        and acl level OAuth scopes.
    </p>
    <p>
        Creating user specific tokens using this interface is <b>not</b> meant to replace the typical
        OAuth authentication flow for 3rd party applications. These credentials should <b>not</b>
        be carelessly supplied to 3rd party vendors. More information is available on our
        <a href="https://aerofs.com/developers">developer website</a>.
    </p>
    </div>

    </p>
    <div class="col-sm-12">
        % if len(user_settings_token) == 0:
            ${access_token_exists()}
        % else:
            ${access_token_does_not_exist()}
        % endif
    </div>
</%def>

<%def name="access_token_exists()">
    <p><a href="#" id="create-access-token">Create a new access token</a></p>
</%def>

<%def name="access_token_does_not_exist()">
    <p>Token: ${user_settings_token}</p>
    <p><a href="#" id="delete-access-token">Delete your current access token</a></p>
</%def>

<div class="row">
    <hr>
    <div class="col-sm-12">
        <% member_since = date.fromtimestamp(long(signup_date / 1000)) %>
        <p>You've been an AeroFS member since
            ${'{0:%B} {1}, {0:%Y}'.format(member_since, member_since.day)}.</p>
    </div>
</div>

<%modal:modal>
    <%def name="id()">reset-password-modal</%def>
    <%def name="title()">Please check your email</%def>
    <%def name="footer()">
        <a href="#" class="btn btn-default" data-dismiss="modal">Close</a>
    </%def>

    An email has been sent to <strong>${userid}</strong>. Please click the link
    in the email to reset your password.
</%modal:modal>

<%modal:modal>
    <%def name="id()">delete-account-modal</%def>
    <%def name="title()">Delete account</%def>
    <%def name="footer()">
        <a href="#" class="btn btn-default" data-dismiss="modal">Cancel</a>
        <a href="#" class="btn btn-danger" id="delete-account-confirmed" data-dismiss="modal">Delete</a>
    </%def>

    Are you sure that you want to delete your AeroFS account? This action cannot be undone.
</%modal:modal>

<%modal:modal>
    <%def name="id()">delete-access-token-modal</%def>
    <%def name="title()">Delete Access Token?</%def>

    <p>This will invalidate your existing credentials.
    You will need to update any systems using the existing values. Are you sure?</p>

    <%def name="footer()">
        <a href="#" class="btn btn-default" data-dismiss="modal">Cancel</a>
        <a href="#" id="delete-access-token-confirm-btn" class="btn btn-danger" data-dismiss="modal">Delete</a>
    </%def>
</%modal:modal>

<%progress_modal:html>
    Please wait while we apply changes...
</%progress_modal:html>

<%block name="scripts">
    <%progress_modal:scripts/>
    <%spinner:scripts/>

    <script>
        $(document).ready(function() {
            initializeProgressModal();

            var updateUserSettingsToken = function(always) {
                window.location.assign('${request.route_path('settings')}');
                always();
            };

            var createAccessToken = function() {
                var $progressModal = $('#${progress_modal.id()}');
                $progressModal.modal('show');
                var always = function() {
                    $progressModal.modal('hide');
                };

                $.post("${request.route_path('json_create_access_token')}", { }
                ).done(function(x) {
                    updateUserSettingsToken(always);
                }).fail(function(xhr) {
                    showErrorMessageUnsafe("Sorry, access token creation failed. " +
                        "Please try again. If this issue persists, please contact " +
                        "<a href='mailto:support@aerofs.com' target='_blank'>" +
                        "support@aerofs.com</a> for assistance.");
                    always();
                });
            };

            var deleteAccessToken = function() {
                var $progressModal = $('#${progress_modal.id()}');
                $progressModal.modal('show');
                var always = function() {
                    $progressModal.modal('hide');
                };

                $.post("${request.route_path('json_delete_access_token')}", { }
                ).done(function(x) {
                    updateUserSettingsToken(always);
                }).fail(function(xhr) {
                    showErrorMessageUnsafe("Sorry, access token deletion failed. " +
                        "Please try again. If this issue persists, please contact " +
                        "<a href='mailto:support@aerofs.com' target='_blank'>" +
                        "support@aerofs.com</a> for assistance.");
                    always();
                });
            };

            var sendPasswordResetEmail = function() {
                $.post("${request.route_path('json_send_password_reset_email')}", { }
                ).done(function() {
                    console.log("sent password reset email successful");
                }).fail(function(xhr) {
                    // Ignore errors as if the email is lost in transit.
                    console.log("sent password reset email failed: " + xhr.status);
                });
            };

            var deleteAccount = function(){
                $.post("${request.route_path('json_delete_user')}", { }
                ).done(function() {
                    showSuccessMessage("You have deleted your account. Goodbye! :-(");
                }).fail(function(xhr) {
                    showErrorMessageUnsafe("Sorry, account deletion failed. " +
                        "Please try again. If this issue persists, please contact " +
                        "<a href='mailto:support@aerofs.com' target='_blank'>" +
                        "support@aerofs.com</a> for assistance.");
                });
            };

            $('#first-name').focus();

            $('#fullname-form').on('submit', function(e){
                e.preventDefault();
                var $btn = $('#fullname-btn');
                setEnabled($btn, false);

                $.post('${request.route_path('json_set_full_name')}',
                    $('#fullname-form').serialize()
                )
                .done(function() {
                    showSuccessMessage("Your name has been updated.");
                })
                .fail(showErrorMessageFromResponse)
                .always(function() {
                    setEnabled($btn, true);
                });
            });

            $('#create-access-token').on('click', function(e){
                e.preventDefault();
                createAccessToken();
            });

            $('#delete-access-token').on('click', function(e){
                e.preventDefault();

                var $modal = $('#delete-access-token-modal');
                $('#delete-access-token-confirm-btn').off().on('click', function() {
                    $modal.modal('hide');
                    deleteAccessToken();
                });
                $modal.modal('show');
            });

            $('#password-reset').on('click', function(e){
                e.preventDefault();
                $('#reset-password-modal').modal('show');
                sendPasswordResetEmail();
            });

            $('#delete-account').on('click', function(e){
                e.preventDefault();
                $('#delete-account-modal').modal('show');
            });
            $('#delete-account-confirmed').on('click', deleteAccount);
        });


        function showAdvancedSettingsOptions() {
            $('#advanced-settings-options').show();
            $('#show-advanced-settings-options').hide();
            $('#hide-advanced-settings-options').show();
        }

        function hideAdvancedSettingsOptions() {
            $('#advanced-settings-options').hide();
            $('#show-advanced-settings-options').show();
            $('#hide-advanced-settings-options').hide();
        }
    </script>
</%block>
