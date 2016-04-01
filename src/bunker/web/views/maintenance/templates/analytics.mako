<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Analytics" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="loader" file="loader.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<h2>Analytics</h2>

<p class="page-block">Analytics allows AeroFS to collect data about product use, so that we can
    continue to develop our product based on the needs of our customers.</p>

<div class="page-block">
    ${analytics_options_form()}
</div>

<%def name="analytics_options_form()">
    <form method="POST" onsubmit="submitForm(); return false;">
        ${csrf.token_input()}

        <label class="radio">
            <input type='radio' id="analytics-option-disable" name='analytics-enabled'
            value='false' style="vertical-align: middle"
                %if not is_analytics_enabled:
                    checked
                %endif
                onchange="disableAnalyticsSelected()"
            >

            Disable analytics
        </label>
        <label class="radio">
            <input type='radio' id="analytics-option-enable" name='analytics-enabled'
            value='true' style="vertical-align: middle"
                %if is_analytics_enabled:
                    checked
                %endif
                onchange="enableAnalyticsSelected()"
            >
            Enable analytics
        </label>
        <div class="row">
            <div class="col-sm-6">
                <button id="save-btn" class="btn btn-primary">Save</button>
            </div>
        </div>
    </form>
</%def>

<%progress_modal:progress_modal>
    <%def name="id()">analytics-modal</%def>
    Configuring the analytics system...
</%progress_modal:progress_modal>

<%block name="scripts">
    <%loader:scripts/>
    <%spinner:scripts/>
    <%progress_modal:scripts/>
    <script>
        $(document).ready(function() {
            initializeProgressModal();
        });

        $('#analytics-modal').modal({
            backdrop: 'static',
            keyboard: false,
            show: false
        });

        function submitForm() {
            var $progress = $('#analytics-modal');
            $progress.modal('show');

            $.post("${request.route_path('json_set_analytics')}",
                    $('form').serialize())
            .done(restartServices)
            .fail(function(xhr) {
                showErrorMessageFromResponse(xhr);
                $progress.modal('hide');
            });
        }

        function restartServices() {
            var $progress = $('#analytics-modal');
            reboot('current', function() {
                $progress.modal('hide');
                showSuccessMessage('New configuration is saved.');
            }, function(xhr) {
                $progress.modal('hide');
                showErrorMessageFromResponse(xhr);
            });
        }

        function disableAnalyticsSelected() {
            ##$('#analytics-endpoint-options').hide();
        }

        function enableAnalyticsSelected() {
            ##$('#analytics-endpoint-options').show();
        }
    </script>
</%block>
