<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Device Restriction" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="bootstrap" file="bootstrap.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<h2>Device Restriction</h2>

<h4>Desktop Client Authorization</h4>

<p>The <strong>AeroFS Private Cloud Device Authorization Subsystem </strong>
    grants IT administrators fine-grained control over which users and devices on
    their network are permitted to run AeroFS desktop clients.
    <a href="https://support.aerofs.com/hc/en-us/articles/202157674" target="_blank">Read more</a>.</p>

<p>This subsystem should not be confused with AeroFS'
    end-to-end encryption and private certificate authority, which is always
    enabled and cannot be configured.
    <a href="https://aerofs.com/security" target="_blank">Read more</a>.</p>

<div class="page-block">
    %if not is_device_restriction_allowed:
        <div class="alert">
        Device authorization is a feature of AeroFS for Business.
        <a href="https://www.aerofs.com/pricing/" target="_blank">Contact us to upgrade
            your appliance</a>.
        </div>
    %else:
        ${device_authorization_options_form()}
    %endif
</div>

<%def name="device_authorization_options_form()">
    <form method="POST" onsubmit="submitDeviceForm(); return false;">
        ${csrf.token_input()}

        <label class="checkbox">
            <input type='hidden' name='enabled' value='false'>
            <input type='checkbox' id="endpoint" name='enabled'
                    value='true'
                %if device_authorization_endpoint_enabled:
                    checked
                %endif
                    onchange="deviceEnableToggled(this); optionsUpdated();"
            >

            Enable Device Authorization
        </label>

        <div id="endpoint-options"
            %if not device_authorization_endpoint_enabled:
                style="display: none;"
            %endif
        >
            <p>Your endpoint: <b><span id="uri"></span></b></p>

            <div class="row">
                <div class="col-sm-4">
                    <label for="host">Hostname:</label>
                    <input class="form-control" id="host"
                        oninput="optionsUpdated();"
                        name="host" type="text" class="form-control"
                        value="${device_authorization_endpoint_host}">
                </div>
                <div class="col-sm-4">
                    <label for="port">Port:</label>
                    <input class="form-control" id="port"
                        oninput="optionsUpdated();"
                        name="port" type="text" class="form-control"
                        value="${device_authorization_endpoint_port}">
                </div>
                <div class="col-sm-4">
                    <label for="path">Path:</label>
                    <input class="form-control" id="path"
                        oninput="optionsUpdated();"
                        name="path" type="text" class="form-control"
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
            <textarea rows="4" class="form-control"
                id="certificate"
                name="certificate">${device_authorization_endpoint_certificate}</textarea>
            <div class="help-block">Supply the endpoint server's certificate
                only if the certificate is <strong>not</strong> publicly signed.
            </div>
        </div>

        <div class="row">
            <div class="col-sm-6">
                <button id="save-btn" class="btn btn-primary">Save</button>
            </div>
        </div>
    </form>
</%def>

<br/>

<h4>Mobile Device Management</h4>

<p>AeroFS can be configured to allow mobile application setup only via a trusted Mobile Device
Management (MDM) proxy. This will prevent mobile app setup on non-MDM-managed devices.
<a href="https://support.aerofs.com/hc/en-us/articles/203160690" target="_blank"
>Read more.
</a></p>

<div class="page-block">
    %if not is_mdm_allowed:
        <div class="alert">
        <p>MDM configuration is a feature of AeroFS for Business.
        <a href="https://www.aerofs.com/pricing/" target="_blank">Contact us to upgrade
        your appliance</a>.</p>
        </div>
    %else:
        ${mdm_options_form()}
    %endif
</div>

<%def name="mdm_options_form()">
    <form method="POST" onsubmit="submitMDMForm(); return false;">
        ${csrf.token_input()}

        <label class="checkbox">
            <input type='hidden' name='enabled' value='false'>
            <input type='checkbox' id="mdm_enable" name='enabled'
                    value='true'
                %if mobile_device_management_enabled:
                    checked
                %endif
                    onchange="mdmEnableToggled(this);"
            >
            Enable MDM Support
        </label>


        <div id="cidr-blocks"
            %if not mobile_device_management_enabled:
                style="display: none;"
            %endif
        >

            <p>Please list CIDR Blocks from which you will allow mobile device access.
            AeroFS will only accept mobile devices that come from a trusted IP address,
            which should be a MDM proxy server (e.g. a MobileIron Sentry server).
            </p>
            <label for="trusted">Trusted CIDR Blocks:</label>
            <div id="trusted">
                % for entry in mobile_device_management_proxies:
                    <input class = "form-control" name="CIDR"
                    onkeyup="newCIDRBox(this)" type="text" value=${entry}>
                % endfor
                <input class="form-control" name="CIDR"
                onkeyup="newCIDRBox(this)" type="text">
            </div>

            <div class="help-block">e.g. <code>192.168.156.0/24</code></div>

        </div>

        <div class="row">
            <div class="col-sm-6">
                <button id="save-btn" class="btn btn-primary">Save</button>
            </div>
        </div>
    </form>
</%def>

<%progress_modal:html>
    Applying changes...
</%progress_modal:html>

<%block name="scripts">
    <%bootstrap:scripts/>
    <%spinner:scripts/>
    <%progress_modal:scripts/>
    <script>
        $(document).ready(function() {
            initializeProgressModal();
            optionsUpdated();
        });

        function submitDeviceForm() {
            var $progress = $('#${progress_modal.id()}');
            $progress.modal('show');

            $.post("${request.route_path('json_set_device_authorization')}",
                    $('form').serialize())
            .done(restartDeviceServices)
            .fail(function(xhr) {
                showErrorMessageFromResponse(xhr);
                $progress.modal('hide');
            });
        }

        function submitMDMForm() {
            var $progress = $('#${progress_modal.id()}');
            $progress.modal('show');

            $.post("${request.route_path('json_set_mobile_device_management')}",
                    $('form').serialize())
            .done(restartMDMServices)
            .fail(function(xhr) {
                showErrorMessageFromResponse(xhr);
                $progress.modal('hide');
            });
        }

        function restartDeviceServices() {
            var $progress = $('#${progress_modal.id()}');
            runBootstrapTask('restart-services-for-device-authorization', function() {
                $progress.modal('hide');
                showSuccessMessage('New configuration is saved.');
            }, function() {
                $progress.modal('hide');
            });
        }

        function newCIDRBox(element){
            if (!element.value) {
                if (element.nextElementSibling!==null) {
                    element.parentNode.removeChild(element);
                }
                return;
            } else if (element.nextElementSibling) {
                return;
            }
            var newTxt = element.cloneNode();
            newTxt.name = 'CIDR';
            newTxt.value='';
            element.parentNode.appendChild(newTxt);
        }

        function restartMDMServices() {
            var $progress = $('#${progress_modal.id()}');
            runBootstrapTask('restart-services-for-mdm', function() {
                $progress.modal('hide');
                showSuccessMessage('New configuration is saved.');
            }, function() {
                $progress.modal('hide');
            });
        }

        function deviceEnableToggled(checkbox) {
            if (checkbox.checked) {
                $('#endpoint-options').show();
            } else {
                $('#endpoint-options').hide();
            }
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

        function mdmEnableToggled(checkbox) {
            if (checkbox.checked) {
                $('#cidr-blocks').show();
            } else {
                $('#cidr-blocks').hide();
            }
        }

        function optionsUpdated() {
            $('span#uri').text(constructURI());
        }
    </script>
</%block>
