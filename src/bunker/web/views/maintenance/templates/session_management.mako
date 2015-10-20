<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Session Management" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="loader" file="loader.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<h2>Session Management</h2>

<p class="page-block">This page allows you to control session length, which dictates how long a user can remain logged
    in to the service without having to re-authenticate. By default, sessions last 30 days. When the "remember me" box is
    clicked, sessions last 1 year. When the checkbox below is selected, sessions last 24 hours, and the remember me box
    is not shown on the login page.</p>

<div class="page-block">
    ${auditing_options_form()}
</div>

<%def name="auditing_options_form()">
    <form method="POST" onsubmit="submitForm(); return false;">
        ${csrf.token_input()}

        <label class="checkbox">
            <input type='checkbox' name='daily-session-expiration' value='daily-session-expiration'
                   %if enable_daily_expiration:
                       checked
                   %endif
            >

            Expire session daily
        </label>

        <div class="form-group">
            <div class="col-sm-6">
                <button id="save-btn" class="btn btn-primary">Save</button>
            </div>
        </div>
    </form>

</%def>

<%progress_modal:html>
Configuring the session setting...
</%progress_modal:html>

<%block name="scripts">
<%loader:scripts/>
<%spinner:scripts/>
<%progress_modal:scripts/>
<script>
        $(document).ready(function() {
            initializeProgressModal();
        });

        function submitForm() {
            var $progress = $('#${progress_modal.id()}');
            $progress.modal('show');

            $.post("${request.route_path('session_management')}",
                    $('form').serialize())
            .done(restartServices)
            .fail(function(xhr) {
                showErrorMessageFromResponse(xhr);
                $progress.modal('hide');
            });
        }

        function restartServices() {
            var $progress = $('#${progress_modal.id()}');
            reboot('current', function() {
                $progress.modal('hide');
                showSuccessMessage('New configuration is saved.');
            }, function(xhr) {
                $progress.modal('hide');
                showErrorMessageFromResponse(xhr);
            });
        }
    </script>
</%block>
