<%namespace name="csrf" file="csrf.mako"/>
<h3>Site Configuration</h3>

<%
    p = request.params.get('p')
    if p == None:
        p = 0
    else:
        p = int(p)
%>

## -----------------------------------------------------
## Pages
## -----------------------------------------------------

%if p == 0:
    <p>Welcome to the AeroFS Site Configuration Interface. This page will guide you through setting up your private AeroFS installation.</p>
    <p>You can start over at any time. Changes will not be visible until they are applied during the final stage.</p>
    <hr/>

    <form method="get">
        <input type="hidden" name="p" value="1"/>
        <button id="submitButton" class="btn btn-primary" type="submit">Let's get started!</button>
    </form>

%endif

%if p == 1:
    <h5>DNS and Hostnames</h5>

    <p>You need to configure DNS in your office to point to the static IP held by this system. If you're using openstack, configure a floating IP for this instance. If you're using VirtualBox, get your static IP from your system console.</p>
    <p>We recommend the following hostname convention:</p>
    <center><p><b>share.&lt;company&gt;.&lt;domain&gt;</b></p></center>
    <p>For example, at the AeroFS office we might choose share.aerofs.com. Once you have your DNS set up, enter the hostname you used press "Next".</p>

    <hr/>
    <form id="hostnameForm">
        ${csrf.token_input()}
        <table width="100%">
            <tr>
            <td width="30%"><label for="base.host.unified">System Hostname:</label></td>
            <td width="70%"><input class="span6" id="base.host.unified" name="base.host.unified" type="text" value=${current_config['base.host.unified']}></td>
            </tr>
        </table>
        <hr/>

        <table width="100%" align="left|right">
        <tr>
        <td>${render_previous_button()}</td>
        <td align="right">${render_next_button("submitHostnameForm()")}</td>
        </tr>
        </table>
    </form>
%endif

%if p == 2:
    <h5>Email</h5>

    <p>AeroFS sends emails to users for many different purposes. On this page you can configure your support email address and SMTP credentials. If you do not specify SMTP information, AeroFS will use its own mail setup.</p>

    <hr/>
    <form id="emailForm">
        ${csrf.token_input()}
        <table width="100%">
            <tr>
            <td width="30%"><label for="base.www.support_email_address">Support Email:</label></td>

            <td width="70%"><input class="span6" id="base.www.support_email_address" name="base.www.support_email_address" type="text" value=${current_config['base.www.support_email_address']}></td>
            </tr>

            <tr>
            <td width="30%"><label for="email.sender.public_host">SMTP Host:</label></td>
            <td width="70%"><input class="span6" id="email.sender.public_host" name="email.sender.public_host" type="text" value=${current_config['email.sender.public_host']}></td>
            </tr>

            <tr>
            <td width="30%"><label for="email.sender.public_username">SMTP Username:</label></td>
            <td width="70%"><input class="span6" id="email.sender.public_username" name="email.sender.public_username" type="text" value=${current_config['email.sender.public_username']}></td>
            </tr>

            <tr>
            <td width="30%"><label for="email.sender.public_password">SMTP Password:</label></td>
            <td width="70%"><input class="span6" id="email.sender.public_password" name="email.sender.public_password" type="password" value=${current_config['email.sender.public_password']}></td>
            </tr>
        </table>
        <hr/>

        <table width="100%" align="left|right">
        <tr>
        <td>${render_previous_button()}</td>
        <td align="right">${render_next_button("submitEmailForm()")}</td>
        </tr>
        </table>
    </form>
%endif

%if p == 3:
    <h5>Browser Certificate</h5>

    <p>You can optionally provide a certificate and key to be used by the AeroFS web server. This will eliminate any certification related error messages when using the AeroFS web interface.</p>

    <hr/>
    <form id="certificateForm">
        ${csrf.token_input()}

            <input type='radio' name='cert.option' value='existing' checked="checked" />
            Use existing certificate and key
            <br/>

            <input type='radio' name='cert.option' value='new' />
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
            <table>
        <hr/>

        <table width="100%" align="left|right">
        <tr>
        <td>${render_previous_button()}</td>
        <td align="right">${render_next_button("submitCertificateForm()")}</td>
        </tr>
        </table>
    </form>

%endif

%if p == 4:
    <h5>Apply Changes</h5>

    <p>When you apply your changes, they will be propagated to various AeroFS system components. You will be redirected automatically once this operation has completed. This operation might take a few seconds.</p>

    <form id="applyForm">
        ${csrf.token_input()}

        <hr/>

        <table width="100%" align="left|right">
        <tr>
        <td>${render_previous_button()}</td>
        <td align="right">
            <button
                onclick='return submitApplyForm();'
                id='nextButton'
                class='btn btn-primary'
                type='submit'>Apply and Finish</button>
        </td>
        </tr>
        </table>

    </form>
%endif

## TODO (MP) need a page to facilitate initial user setup (can't expect them to navigate to the marketing page).

<script>

// -----------------------------------------------------
// Form Submission Scripts
// -----------------------------------------------------

function submitHostnameForm()
{
    disableButtons();

    if (verifyPresence("base.host.unified", "Must specify a system hostname."))
    {
        var $form = $('#hostnameForm');
        var serializedData = $form.serialize();

        doPost("${request.route_path('json_config_hostname')}",
            serializedData, gotoNextPage);
    }

    event.preventDefault();
}

function submitEmailForm()
{
    disableButtons();

    if (verifyPresence("base.www.support_email_address", "Must specify a support email address."))
    {
        var $form = $('#emailForm');
        var serializedData = $form.serialize();

        doPost("${request.route_path('json_config_email')}",
            serializedData, gotoNextPage);
    }

    event.preventDefault();
}

var certificateData = null;
var keyData = null;

function setCertificateData(data)
{
    certificateData = data;

    if (keyData != null)
    {
        postCertificateData();
    }
}

function setKeyData(data)
{
    keyData = data;

    if (certificateData != null)
    {
        postCertificateData();
    }
}

function submitCertificateForm()
{
    disableButtons();

    var choice = $('input[name="cert.option"]:checked').val();
    if (choice == 'existing')
    {
        if (verifyAbsence("server.browser.certificate") && verifyAbsence("server.browser.key"))
        {
            gotoNextPage("${p}");
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
            // Certificate.
            var certificateFile = document.getElementById("server.browser.certificate").files[0];
            var certificateReader = new FileReader();
            certificateReader.onload = function() {
                setCertificateData(this.result);
            }
            certificateReader.readAsBinaryString(certificateFile);

            // Key.
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

function postCertificateData()
{
    var $form = $('#certificateForm');
    var serializedData = $form.serialize();

    doPost("${request.route_path('json_config_certificate')}",
        serializedData + "&server.browser.certificate=" + certificateData + "&server.browser.key=" + keyData,
        gotoNextPage);
}

var applySerializedData;

function submitApplyForm()
{
    // TODO (MP) need more user feedback than this. Need a spinner or something.

    showSuccessMessage("Please wait while your change is applied. You will be redirected automatically.");

    var $form = $('#applyForm');
    applySerializedData = $form.serialize();

    disableButtons();
    $(document).ready(function(){
        $("a").css("cursor", "arrow").click(false);
        $(":input").prop("disabled", true);
    });

    doPost("${request.route_path('json_config_apply')}", applySerializedData, initializeBootstrapPoller);
}

var interval;
var firstRun = true;
function initializeBootstrapPoller()
{
    interval = self.setInterval(function() {doBootstrapPoll()}, 1000);
}

function doBootstrapPoll()
{
    if (firstRun)
    {
        firstRun = false;
    }
    else
    {
        doPost("${request.route_path('json_config_poll')}", applySerializedData, finalizeConfigurationSetupIfCompleted);
    }
}

function finalizeConfigurationSetupIfCompleted(response)
{
    if (response['completed'] == true)
    {
        window.clearInterval(interval);

        // TODO (MP) not sure why the finalize call fails sometimes after the poller finishes.
        setTimeout(function()
        {
            doPost("${request.route_path('json_config_finalize')}", applySerializedData, redirectToHome);
        }, 1000);
    }
}

function redirectToHome()
{
    // TODO (MP) Add a better success message here. Perhaps integrate with spinner.
    // Wait for the uwsgi reload to finish then redirect to home.
    setTimeout(function()
    {
        alert("Configured successfully. You will be redirected to the login page.");
        // TODO (MP) can improve this too, but for now leaving as-is, since this code will change in the next iteration anyway.
        window.location.href = "${request.route_path('login')}";
    }, 3000);
}

// -----------------------------------------------------
// General Scripts
// -----------------------------------------------------

function verifyAbsence(elementID, message)
{
    var v = document.getElementById(elementID).value;
    if (v == null || v == "")
    {
        // Absent == true.
        return true;
    }

    return false;
}

function verifyPresence(elementID, message)
{
    var v = document.getElementById(elementID).value;
    if (v == null || v == "")
    {
        displayError(message);
        return false;
    }

    return true;
}

function doPost(postRoute, postData, callback)
{
    $.post(postRoute, postData)
    .done(function (response)
    {
        var error = response['error'];
        if (error)
        {
            displayError(error);
        }
        else
        {
            callback(response);
        }
    })
    .fail(function (jqXHR, textStatus, errorThrown)
    {
        displayError("Error: " + textStatus + " " + errorThrown);
    });
}

function gotoNextPage()
{
    var nextPage = parseInt("${p}") + 1;
    var next = "${request.route_path('site_config')}" + "?p=" + nextPage;
    window.location.href = next;
}

function disableButtons()
{
    var $nextButton = $("#nextButton");
    $nextButton.attr("disabled", "disabled");

    var $previousButton = $("#previousButton");
    $previousButton.attr("disabled", "disabled");
}

function enableButtons()
{
    var $nextButton = $("#nextButton");
    $nextButton.removeAttr("disabled");

    var $previousButton = $("#previousButton");
    $previousButton.removeAttr("disabled");
}

function displayError(error)
{
    enableButtons();
    showErrorMessage(error);
}

</script>

## -----------------------------------------------------
## Defs
## -----------------------------------------------------

<%def name="render_previous_button()">
    <%
        previous_page = p - 1

        if previous_page == 0:
            previous_page_string = ''
        else:
            previous_page_string = '?p=' + str(previous_page)
    %>

    <button
        onclick="window.location='/site_config${previous_page_string}'"
        id='previousButton'
        class='btn btn-primary'
        type='button'>Previous</button>
</%def>

<%def name="render_next_button(javascriptCallback)">
    <button
        onclick='return ${javascriptCallback};'
        id='nextButton'
        class='btn btn-primary'
        type='submit'>Next</button>
</%def>
