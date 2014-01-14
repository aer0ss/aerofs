<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="setup_common.mako"/>

<h4>Browser certificate:</h4>

<form method="POST" onsubmit="submitForm(); return false;">
    ${csrf.token_input()}

    <label class="radio">
        <input type='radio' id="cert-option-existing" name='cert.option'
                value='existing' checked onchange="useInstalledCertSelected()">
        %if is_configuration_initialized:
            Use installed certificate and key
        %else:
            Use pre-installed, self-signed certificate and key
        %endif
        <p class="main-option-footnote">Use your Web browser to check certificate details</p>
    </label>
    <label class="radio">
        <input type='radio' id="cert-option-new" name='cert.option' value='new'
                onchange="useNewCertSelected()">
        Upload new certificate and key

        <div class="row-fluid" style="margin-top: 8px;">
            <div class="span6">
                <label for="server.browser.certificate">Certificate file:</label>
                <input type="file" id="server.browser.certificate" name="server.browser.certificate" disabled/>
            </div>
            <div class="span6">
                <label for="server.browser.certificate">Key file:</label>
                <input type="file" id="server.browser.key" name="server.browser.key" disabled/>
            </div>
        </div>
    </label>

    <p style="margin-top: 10px">Provide publicly signed certificate and key to eliminate certification error messages when browsing the AeroFS Web interface. We require valid x509 SSL certificate and private key files in PEM format.</p>
    <hr />
    ${common.render_next_button()}
    ${common.render_previous_button()}
</form>

<%def name="scripts()">
    <script>
        function useInstalledCertSelected() {
            ## disable the file upload and remove set files
            $('input:file').attr("disabled", "disabled").val("");
        }

        function useNewCertSelected() {
            $('input:file').removeAttr("disabled");
        }

        function submitForm() {
            disableNavButtons();

            var choice = $(':input[name=cert.option]:checked').val();
            if (choice == 'existing') {
                gotoNextPage();
            } else if (choice == 'new') {
                if (verifyPresence("server.browser.certificate", "Please specify a certificate file.") &&
                        verifyPresence("server.browser.key", "Please specify a key file.")) {
                    ## Certificate.
                    var certificateFile = document.getElementById("server.browser.certificate").files[0];
                    var certificateReader = new FileReader();
                    certificateReader.onload = function() {
                        setCertificateData(this.result);
                    };
                    certificateReader.readAsBinaryString(certificateFile);

                    ## Key.
                    var keyFile = document.getElementById("server.browser.key").files[0];
                    var keyReader = new FileReader();
                    keyReader.onload = function() {
                        setKeyData(this.result);
                    };
                    keyReader.readAsBinaryString(keyFile);
                }
            }
        }

        var certificateData = null;
        var keyData = null;

        ## TOOD (WW) use multipart data upload
        function formatPostData(data) {
            return data.replace(/\+/g, '%2B');
        }

        function setCertificateData(data) {
            certificateData = formatPostData(data);

            if (keyData != null) {
                postCertificateData();
            }
        }

        function setKeyData(data) {
            keyData = formatPostData(data);

            if (certificateData != null) {
                postCertificateData();
            }
        }

        function postCertificateData() {
            var serializedData = $('form').serialize();

            doPost("${request.route_path('json_setup_certificate')}",
                serializedData + "&server.browser.certificate=" + certificateData + "&server.browser.key=" + keyData,
                gotoNextPage, enableNavButtons);
        }
    </script>
</%def>
