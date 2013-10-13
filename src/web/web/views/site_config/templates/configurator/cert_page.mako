<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>

<h4>Browser Certificate</h4>

<p>You can optionally provide a certificate and key to be used by the AeroFS web server. This will eliminate any certification related error messages when using the AeroFS web interface.</p>

<hr/>
<form id="certificateForm">
    ${csrf.token_input()}

    <input type='radio' name='cert.option' value='existing' checked="checked">
    Use existing certificate and key
    <br/>

    <input type='radio' name='cert.option' value='new'>
    Use new certificate and key
    <br/>
    <br/>

    <table width="100%">
        <tr>
            <td width="50%">Certificate file</td>
            <td width="50%">Key file</td>
        </tr>
        <tr>
            <td width="50%"><input type="file" id="server.browser.certificate" name="server.browser.certificate" /></td>
            <td width="50%"><input type="file" id="server.browser.key" name="server.browser.key" /></td>
        </tr>
    </table>
    <hr/>

    ${common.render_previous_button(page)}
    ${common.render_next_button("submitCertificateForm()")}
</form>

<script type="text/javascript">
    function submitCertificateForm()
    {
        disableButtons();

        var choice = $('input[name="cert.option"]:checked').val();
        if (choice == 'existing')
        {
            if (verifyAbsence("server.browser.certificate") && verifyAbsence("server.browser.key"))
            {
                gotoNextPage();
            }
            else
            {
                document.getElementById("certificateForm").reset();
                displayError("Do not specify an upload file if using existing the certificate.");
            }
        }
        else if (choice == 'new')
        {
            if (verifyPresence("server.browser.certificate", "Must specify a certificate file.") &&
                verifyPresence("server.browser.key", "Must specify a key file."))
            {
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

    function formatPostData(data)
    {
        return data.replace(/\+/g, '%2B');
    }

    function setCertificateData(data)
    {
        certificateData = formatPostData(data);

        if (keyData != null)
        {
            postCertificateData();
        }
    }

    function setKeyData(data)
    {
        keyData = formatPostData(data);

        if (certificateData != null)
        {
            postCertificateData();
        }
    }

    function postCertificateData()
    {
        var $form = $('#certificateForm');
        var serializedData = $form.serialize();

        doPost("${request.route_path('json_config_certificate')}",
            serializedData + "&server.browser.certificate=" + certificateData + "&server.browser.key=" + keyData,
            gotoNextPage);
    }
</script>