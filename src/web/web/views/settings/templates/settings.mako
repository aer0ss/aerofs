<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Settings" %>

<%namespace name="modal" file="modal.mako"/>

<%! from datetime import date %>

<div class="page-block">
    <h2>Settings</h2>
    <% member_since = date.fromtimestamp(long(signup_date / 1000)) %>
    <p>You've been a member since
        ${'{0:%b} {1}, {0:%Y}'.format(member_since, member_since.day)}.</p>
</div>

<form class="form-horizontal page-block" id="fullname-form" method="post"
        onsubmit="setFullname(); return false;">
    ${self.csrf.token_input()}
    <fieldset>
        ## <legend>User info</legend>
        <div class="control-group">
            <label class="control-label" for="first-name">First name:</label>
            <div class="controls">
                <input type="text" id="first-name" name="first-name"
                        value="${first_name}">
            </div>
        </div>
        <div class="control-group">
            <label class="control-label" for="last-name">Last name:</label>
            <div class="controls">
                <input type="text" id="last-name" name="last-name"
                        value="${last_name}">
            </div>
        </div>
        <div class="control-group">
            <div class="controls">
                <button class="btn" id="fullname-btn">Update</button>
            </div>
        </div>
        <div class="control-group">
            <div class="controls">
                <a href="#" onclick="sendPasswordResetEmail(); return false;">
                    Forgot or change your password?
                </a>
            </div>
        </div>
    </fieldset>
</form>

<%modal:modal>
    <%def name="id()">reset-password-modal</%def>
    <%def name="title()">Please check your email</%def>
    <%def name="footer()">
        <a href="#" class="btn" data-dismiss="modal">Close</a>
    </%def>

    An email has been sent to <strong>${userid}</strong>. Please click the link
    in the email to reset your password.
</%modal:modal>

<%block name="scripts">
    <script>
        $(document).ready(function() {
            $('#first-name').focus();
        });

        function sendPasswordResetEmail() {
            $('#reset-password-modal').modal('show');

            $.post('${request.route_path('json_send_password_reset_email')}', { }
            ).done(function() {
                console.log("sent password reset email successful");
            }).fail(function(xhr) {
                ## Ignore errors as if the email is lost in transit.
                console.log("sent password reset email failed: " + xhr.status);
            });
        }

        function setFullname() {
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
        }
    </script>
</%block>
