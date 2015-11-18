<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="setup_common.mako"/>

<h4>Browser certificate</h4>

<form method="POST" role="form" onsubmit="submitForm(); return false;">
    ${csrf.token_input()}
    <div class="row">
        <div class="col-sm-12">
            <label class="radio">
                <input type='radio' id="cert-option-existing" name='cert.option'
                        value='existing' checked onchange="useInstalledCertSelected()">
                %if is_configuration_completed or restored_from_backup:
                    Use previously installed certificate and key
                %else:
                    Use pre-installed, self-signed certificate and key
                %endif
            </label>
            <label class="radio">
                <input type='radio' id="cert-option-new" name='cert.option' value='new'
                        onchange="useNewCertSelected()">
                Upload new certificate and key:
            </label>
            <div class="form-group">
                <div class="col-sm-6">
                    <label for="cert-selector">Certificate file:</label>
                    <input type="file" id="cert-selector" disabled/>
                    <input type="hidden" id="server-browser-certificate" name="server.browser.certificate" />
                </div>
                <div class="col-sm-6">
                    <label for="key-selector">Key file:</label>
                    <input type="file" id="key-selector" disabled/>
                    <input type="hidden" id="server-browser-key" name="server.browser.key" />
                </div>

                <div class="col-sm-12">
                    <p class="help-block">Provide publicly signed certificate and key to eliminate
                    certification error messages when your users browse the AeroFS Web
                    site. We require valid x509 SSL certificate and private key files in
                    the PEM format.</p>
                </div>
            </div>
        </div>
    </div>
    ${common.render_next_prev_buttons()}
</form>

<%def name="scripts()">
    <script>
        $(document).ready(function() {
            linkFileSelectorToField('#cert-selector', '#server-browser-certificate');
            linkFileSelectorToField('#key-selector', '#server-browser-key');
        });

        function useInstalledCertSelected() {
            ## disable the file upload and remove set files
            $('input:file').attr("disabled", "disabled").val("");
            $('input:hidden').val("");
        }

        function useNewCertSelected() {
            $('input:file').removeAttr("disabled");
        }

        function submitForm() {
            disableNavButtons();

            var choice = $('input[name="cert.option"]:checked').val();
            if (choice == 'existing' || (choice == 'new' &&
                verifyPresence("server-browser-certificate", "Please specify a certificate file.") &&
                verifyPresence("server-browser-key", "Please specify a key file."))) {
                    doPost("${request.route_path('json_setup_certificate')}",
                        $('form').serialize(), gotoNextPage, enableNavButtons);
            }
        }
    </script>
</%def>
