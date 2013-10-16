<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

<h4>Browser certificate:</h4>

<form id="certificateForm" method="POST">
    ${csrf.token_input()}

    <label class="radio">
        <input type='radio' name='cert.option' value='existing' checked
                onchange="useInstalledCertSelected()">
        Use installed certificate and key
    </label>
    <label class="radio">
        <input type='radio' name='cert.option' value='new'
                onchange="useNewCertSelected()">
        Use new certificate and key
    </label>

    <div class="row-fluid">
        <div class="span6">
            <label for="server.browser.certificate">Certificate file:</label>
            <input type="file" id="server.browser.certificate" name="server.browser.certificate" disabled/>
        </div>
        <div class="span6">
            <label for="server.browser.certificate">Key file:</label>
            <input type="file" id="server.browser.key" name="server.browser.key" disabled/>
        </div>
    </div>

    <p style="margin-top: 10px">You can optionally provide your own certificate and key to be used by the AeroFS web server. This will eliminate certification related error messages when using the AeroFS Web interface. Valid x509 SSL certificate and private key files are required. The certificate must be in PEM format.</p>

    <hr/>

    ${common.render_previous_button(page)}
    ${common.render_next_button("submitCertificateForm()")}
</form>

<script type="text/javascript">
    function useInstalledCertSelected() {
        $('input:file').attr("disabled", "disabled").val("");
    }

    function useNewCertSelected() {
        $('input:file').removeAttr("disabled");
    }

    function submitCertificateForm() {
        disableButtons();

        var choice = $('input[name="cert.option"]:checked').val();
        if (choice == 'existing') {
            if (verifyAbsence("server.browser.certificate") && verifyAbsence("server.browser.key")) {
                gotoNextPage();
            } else {
                document.getElementById("certificateForm").reset();
                displayError("Do not specify an upload file if using existing the certificate.");
            }
        } else if (choice == 'new') {
            if (verifyPresence("server.browser.certificate", "Please specify a certificate file.") &&
                    verifyPresence("server.browser.key", "Please specify a key file.")) {
                ## Certificate.
                var certificateFile = document.getElementById("server.browser.certificate").files[0];
                var certificateReader = new FileReader();
                certificateReader.onload = function() {
                    setCertificateData(this.result);
                }
                certificateReader.readAsBinaryString(certificateFile);

                ## Key.
                var keyFile = document.getElementById("server.browser.key").files[0];
                var keyReader = new FileReader();
                keyReader.onload = function() {
                    setKeyData(this.result);
                }
                keyReader.readAsBinaryString(keyFile);
            }
        }

        event.preventDefault();
    }

    var certificateData = null;
    var keyData = null;

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
        var $form = $('#certificateForm');
        var serializedData = $form.serialize();

        doPost("${request.route_path('json_setup_certificate')}",
            serializedData + "&server.browser.certificate=" + certificateData + "&server.browser.key=" + keyData,
            gotoNextPage, enableButtons);
    }
</script>
