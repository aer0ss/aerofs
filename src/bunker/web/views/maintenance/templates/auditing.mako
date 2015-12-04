<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Auditing" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="loader" file="loader.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<h2>Auditing</h2>

<p class="page-block">Auditing allows admins to monitor and track user activities.
    The audit log provides a real time stream of a wide range of user activities
    and file syncing events, to 3rd-party logging systems such as Splunk.
    <a href="https://support.aerofs.com/hc/en-us/articles/204862650" target="_blank">Read more</a>.</p>

<div class="page-block">
    ${auditing_options_form()}
</div>

<%def name="auditing_options_form()">
    <form method="POST" onsubmit="submitForm(); return false;">
        ${csrf.token_input()}

        <label class="radio">
            <input type='radio' id="audit-option-disable" name='audit-enabled'
            value='false' style="vertical-align: middle"
                %if not is_audit_enabled:
                    checked
                %endif
                onchange="disableSelected()"
            >

            Disable auditing
        </label>

        <label class="radio">
            <input type='radio' id="audit-option-enable" name='audit-enabled'
            value='true' style="vertical-align: middle"
                %if is_audit_enabled:
                    checked
                %endif
                onchange="enableSelected()"
            >

            Enable auditing
        </label>

        <div id="external-endpoint-options"
            %if not is_audit_enabled:
                style="display: none;"
            %endif
        >
            <hr/>
            <h4>Downstream Options</h4>
            <p>Auditing requires a downstream server to which AeroFS will
                publish audit events. The events will be sent as newline-separated
                JSON documents over tcp (or tcp-ssl):</p>

            <div class="row">
                <div class="col-sm-6">
                    <label for="audit-downstream-host">Hostname:</label>
                    <input class="form-control" id="audit-downstream-host"
                        name="audit-downstream-host" type="text" required
                        value="${downstream_host}">
                </div>
                <div class="col-sm-3">
                    <label for="audit-downstream-port">Port:</label>
                    <input class="form-control" id="audit-downstream-port"
                        name="audit-downstream-port" type="text" required
                        value="${downstream_port}">
                </div>
            </div>

            <div class="row">
                <div class="col-sm-6">
                    <div class="checkbox">
                        <label for="audit-downstream-ssl-enabled">
                            <input id="audit-downstream-ssl-enabled"
                                name="audit-downstream-ssl-enabled" type="checkbox"
                                %if downstream_ssl_enabled:
                                    checked
                                %endif
                            >Use SSL encryption
                        </label>
                    </div>
                </div>
            </div>

            <div class="row">
                <div class="col-sm-12">
                    <label for="audit-downstream-certificate">Server certificate for SSL (optional):</label>
                    <textarea rows="4" class="form-control"
                        id="audit-downstream-certificate"
                        name="audit-downstream-certificate">${downstream_cert}</textarea>
                    <div class="help-block">Supply the downstream server's certificate
                        only if the certificate is <strong>not</strong> publicly signed.
                    </div>
                </div>
            </div>
        </div>

        <div class="row">
            <div class="col-sm-6">
                <button id="save-btn" class="btn btn-primary">Save</button>
            </div>
        </div>
    </form>
</%def>

<%progress_modal:html>
    Configuring the auditing system...
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

            $.post("${request.route_path('json_set_auditing')}",
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

        function disableSelected() {
            $('#external-endpoint-options').hide();
        }

        function enableSelected() {
            $('#external-endpoint-options').show();
        }
    </script>
</%block>
