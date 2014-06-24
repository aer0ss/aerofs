<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Set up Two-Factor Authentication" %>

<h2>Two-Factor Authentication Setup</h2>

<div class="row">
    <div class="col-sm-12">
        <h4>Add AeroFS to your Two-Factor Authentication App</h4>
        <p>You'll need to install a two-factor authentication application on
        your phone or tablet.  For more information, see our
        <a href="https://support.aerofs.com/hc/en-us/articles/202775400">support
        article on two-factor authentication.</a></p>
    </div>
</div>
<div class="row">
    <div class="col-sm-12">
    <h4>Configure the app</h4>
    <p>Open your two-factor authentication app and add your AeroFS account by scanning the QR code.</p>
    <img src="${qr_image_data}">
    <p>If you can't use a QR code, enter this text code: ${secret}</p>
    </div>
</div>
<div class="row">
    <div class="col-sm-12">
        <h4>Enable two-factor authentication</h4>
        <p>Enter the 6-digit code that the application generates:</p>
        <form class="form-horizontal" role="form" action="${request.route_path('two_factor_setup')}" method="post">
            ${self.csrf.token_input()}
            ## We include the encrypted, hmac'd secret so SP doesn't have to
            ## allow any way to extract the secret after the initial generation.
            <input type="hidden" value="${secret_blob}" name="boxed_secret">
            <fieldset>
                <div class="form-group">
                    <div class="col-sm-4">
                        <input class="input-medium form-control" name="${url_param_code}" type="text" maxlength="6" placeholder="123456"/>
                    </div>
                    <div class="col-sm-4">
                        <button id="tfa_enable_button" class="btn btn-primary" type="submit">Enable</button>
                    </div>
                </div>
            </fieldset>
        </form>
    </div>
</div>
