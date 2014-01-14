<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="setup_common.mako"/>

<%! from web.util import str2bool %>

<h4>Auditing:</h4>

<p>If you enable this feature, system administrators will be able to monitor and
track user activities within the AeroFS system.</p>

<form method="POST" onsubmit="submitForm(); return false;">
    ${csrf.token_input()}

    <label class="radio">
        <input type='radio' id="audit-option-disable" name='audit-enabled'
        value='false'
            %if not is_audit_enabled:
                checked
            %endif
            onchange="disableSelected()"
        >

        Disable Auditing
    </label>

    <label class="radio">
        <input type='radio' id="audit-option-enable" name='audit-enabled'
                value='true'
            %if not is_audit_allowed:
                disabled
            %endif
            %if is_audit_enabled:
                checked
            %endif
            onchange="enableSelected()"
        >

        Enable Auditing

        %if not is_audit_allowed:
            <div class="main-option-footnote">
               To be able to enable auditing, you must request the audit feature
               prior to obtaining your AeroFS license file.
           </div>
        %endif
    </label>

    <div id="external-endpoint-options"
        %if not is_audit_enabled:
            class="hide"
        %endif
    >
        <hr/>
        <h4>Downstream Options</h4>
        <p>You can optionally specify a downstream service to which we will
        publish audit events. The events will be encoded in JSON format.</p>

        <div class="row-fluid">
            <div class="span8">
                <label for="audit-downstream-host">Host (optional):</label>
                <input class="input-block-level" id="audit-downstream-host"
                    name="audit-downstream-host" type="text"
                    value="${current_config['base.audit.downstream_host']}">
            </div>
            <div class="span4">
                <label for="audit-downstream-port">Port (optional):</label>
                <input class="input-block-level" id="audit-downstream-port"
                    name="audit-downstream-port" type="text"
                    value="${current_config['base.audit.downstream_port']}">
            </div>
        </div>

        <label for="audit-downstream-ssl-enabled" class="checkbox">
            <input id="audit-downstream-ssl-enabled"
                name="audit-downstream-ssl-enabled" type="checkbox"
                %if str2bool(current_config['base.audit.downstream_ssl_enabled']):
                    checked
                %endif
            >Use SSL encryption
        </label>

        <label for="audit-downstream-certificate">Server certificate for SSL (optional):</label>
        <textarea rows="4" class="input-block-level"
            id="audit-downstream-certificate"
            name="audit-downstream-certificate"
            >${current_config['base.audit.downstream_certificate'].replace('\\n', '\n')}</textarea>
        <div class="input-footnote">Supply the downstream server's certificate
            only if the certificate is <strong>not</strong> publicly signed.
        </div>
    </div>

    <hr />
    ${common.render_next_button()}
    ${common.render_previous_button()}
</form>

<%def name="scripts()">
    <script>
        function submitForm() {
            disableNavButtons();

            var choice = $(':input[name=audit-enabled]:checked').val();

            var serializedData = $('form').serialize();
            doPost("${request.route_path('json_setup_audit')}",
                    serializedData, gotoNextPage, enableNavButtons);
        }

        function disableSelected() {
            $('#external-endpoint-options').hide();
        }

        function enableSelected() {
            $('#external-endpoint-options').show();
        }
    </script>
</%def>
