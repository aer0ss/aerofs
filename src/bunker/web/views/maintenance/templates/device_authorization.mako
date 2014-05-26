<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Device Authorization" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="bootstrap" file="bootstrap.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<h2>Device Authorization</h2>

<p class="page-block">The <b>AeroFS Private Cloud Device Authorization Subsystem </b>
grants IT administrators fine-grained control over which users and devices on
their network are permitted to run AeroFS desktop clients.
<a href="https://support.aerofs.com/hc/en-us/articles/202157674" target="_blank">Read more</a>.</p></P>

<p class="page-block">This subsystem should not be confused with AeroFS'
end-to-end encryption and private certificate authority, which is always
enabled and cannot be configured.
<a href="https://aerofs.com/security" target="_blank">Read more</a>.</p></P>

<div class="page-block">
    ${device_authorization_options_form()}
</div>

<%def name="device_authorization_options_form()">
    <form method="POST" onsubmit="submitForm(); return false;">
        ${csrf.token_input()}

        <label class="radio">
            <input type='radio' id="endpoint_disable" name='enabled'
                    value='false'
                %if not device_authorization_endpoint_enabled:
                    checked
                %endif
                    onchange="disableSelected(); optionsUpdated();"
            >

            Disable device authorization
        </label>

        <label class="radio">
            <input type='radio' id="endpoint_enable" name='enabled'
                    value='true'
                %if device_authorization_endpoint_enabled:
                    checked
                %endif
                    onchange="enableSelected(); optionsUpdated();"
            >

            Enable device authorization
        </label>

        <div id="endpoint-options"
            %if not device_authorization_endpoint_enabled:
                class="hide"
            %endif
        >
            <hr/>
            <h4>Device Authorization Endpoint Options</h4>

            <p>Your endpoint: <b><span id="uri"></span></b></p>

            <div class="row-fluid">
                <div class="span5">
                    <label for="host">Hostname:</label>
                    <input class="input-block-level" id="host"
                        oninput="optionsUpdated();"
                        name="host" type="text"
                        value="${device_authorization_endpoint_host}">
                </div>
                <div class="span3">
                    <label for="port">Port:</label>
                    <input class="input-block-level" id="port"
                        oninput="optionsUpdated();"
                        name="port" type="text"
                        value="${device_authorization_endpoint_port}">
                </div>
                <div class="span4">
                    <label for="path">Path:</label>
                    <input class="input-block-level" id="path"
                        oninput="optionsUpdated();"
                        name="path" type="text"
                        value="${device_authorization_endpoint_path}">
                </div>
            </div>

            <label for="use_ssl" class="checkbox">
                <input id="use_ssl"
                    onclick="optionsUpdated();"
                    name="use_ssl" type="checkbox"
                    %if device_authorization_endpoint_use_ssl:
                        checked
                    %endif
                >Use SSL encryption
            </label>

            <label for="certificate">Server certificate for SSL (optional):</label>
            <textarea rows="4" class="input-block-level"
                id="certificate"
                name="certificate">${device_authorization_endpoint_certificate}</textarea>
            <div class="input-footnote">Supply the endpoint server's certificate
                only if the certificate is <strong>not</strong> publicly signed.
            </div>
        </div>

        <hr/>
        <button id="save-btn" class="btn btn-primary">Save</button>
    </form>
</%def>

<%progress_modal:html>
    Configuring the device authorization subsystem...
</%progress_modal:html>

<%block name="scripts">
    <%bootstrap:scripts/>
    <%spinner:scripts/>
    <%progress_modal:scripts/>
    <script>
        $(document).ready(function() {
            initializeProgressModal();
        });

        function submitForm() {
            var $progress = $('#${progress_modal.id()}');
            $progress.modal('show');

            $.post("${request.route_path('json_set_device_authorization')}",
                    $('form').serialize())
            .done(restartServices)
            .fail(function(xhr) {
                showErrorMessageFromResponse(xhr);
                $progress.modal('hide');
            });
        }

        function restartServices() {
            var $progress = $('#${progress_modal.id()}');
            runBootstrapTask('restart-services-for-device-authorization', function() {
                $progress.modal('hide');
                showSuccessMessage('New configuration is saved.');
            }, function() {
                $progress.modal('hide');
            });
        }

        function disableSelected() {
            $('#endpoint-options').hide();
        }

        function enableSelected() {
            $('#endpoint-options').show();
        }

        function constructURI() {
            var host = $('#host').val();
            var port = $('#port').val();
            var path = $('#path').val().replace(/^\/|\/$/g, '');
            var use_ssl = $('#use_ssl').is(':checked');

            if (host.length == 0) {
                host = "<host>";
            }
            if (port.length == 0) {
                port = "<port>";
            }

            uri = (use_ssl ? "https" : "http") + "://" +
                (host + ":" + port + "/" + path +
                "/device/v1.0/<string:email>/authorized").replace(/\/+/g, '\/');

            return uri;
        }

        function optionsUpdated() {
            $('span#uri').text(constructURI());
        }

        window.onload = optionsUpdated();
    </script>
</%block>
