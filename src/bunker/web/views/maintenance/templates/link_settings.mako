<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Password Restriction" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="loader" file="loader.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<h2>Link Sharing</h2>

<div class="page-block">
    <p>Link sharing is a lightweight way of sharing a file or a folder. Users love link sharing because
    they can paste the link into existing conversations in email or chat. Changing these settings will
    affect all future links and will not affect any existing links' settings. </p>

    <h4>Link Access</h4>


    <form id="set-require-login-form" method="POST" onsubmit="submit:setRequireLogin(); return false;">
        ${csrf.token_input()}

        <div class="row">
            <div class="col-sm-8">
                  <label>By default, users who have the link can access it without sign-in.</label>
            </div>
        </div>

        <div class="radio">
            <label>
                <input type='radio' name='links_require_login' value="false"
                    %if not links_require_login:
                    checked
                    %endif
                />
                Yes
            </label>
        </div>
        <div class="radio">
            <label>
                <input id="require-login-true-radio" type='radio' name='links_require_login' value="true"
                    %if links_require_login:
                    checked
                    %endif
                />
                No
            </label>
        </div>

        <div class="row">
            <div class="col-sm-4">
                <button type="submit" id="save-btn" class="btn btn-primary">Save</button>
            </div>
        </div>

    </form>
</div>

<%progress_modal:progress_modal>
    <%def name="id()">link-modal</%def>
    Applying changes...
</%progress_modal:progress_modal>

<%block name="scripts">
    <%loader:scripts/>
    <%spinner:scripts/>
    <%progress_modal:scripts/>
    <script>
       $(document).ready(function() {
            initializeProgressModal();
        });

        $('#link-modal').modal({
            backdrop: 'static',
            keyboard: false,
            show: false
        });

        function setRequireLogin() {
            // Set up modal
            var $progress = $('#link-modal');
            $progress.modal('show');

            $.post("${request.route_path('json_set_require_login')}",
                $('#set-require-login-form').serialize())
            .success(function () {
                reboot('current', function success () {
                    $progress.modal('hide');
                    showSuccessMessage('New configuration is saved.')
                }, function fail(xhr){
                    $progress.modal('hide');
                    showErrorMessageFromResponse(xhr);
                });
            })
            .fail(function(xhr) {
                $progress.modal('hide');
                showErrorMessageFromResponse(xhr);
            });
        }
    </script>
</%block>
