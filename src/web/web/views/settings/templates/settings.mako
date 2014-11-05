<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Settings" %>

<%namespace name="modal" file="modal.mako"/>

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
        <a href="#" id="password-reset">
            Reset your password
        </a>
        <br><br>
    </div>
    <div class="col-sm-12">
        <a href="#" id="delete-account">
            Delete your account
        </a>
    </div>
</div>

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

<%block name="scripts">
    <script>
        $(document).ready(function() {
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
    </script>
</%block>
