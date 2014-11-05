<%namespace name="csrf" file="csrf.mako" import="token_input"
        inheritable="True"/>
<%def name="how_it_works()">
    <div class="row">
        <div class="col-sm-12">
            <h3>Using Two-Factor Authentication</h3>
        </div>
    </div>
    <div class="row">
        <div class="col-sm-12">
            <h4>Log in as normal</h4>
            ## Should this text change depending on the value of lib.authenticator?
            ## It might be a liar for external users in the openid/password combo world...
            <p>Enter your username and password as normal.</p>
        </div>
        <div class="col-sm-12">
            <h4>Enter code from phone</h4>
            <p>You'll be prompted to enter an additional six-digit code that you'll
            read from your mobile device to verify your identity.</p>
        </div>
        <div class="col-sm-12">
            <h4>You're logged in</h4>
            <p>Use AeroFS as normal.</p>
        </div>
    </div>
    %if not two_factor_enforced:
    <div class="row">
        <div class="col-sm-12">
        <form method="post" class="form-inline" role="form" action="${request.route_path('two_factor_setup')}">
            ${self.csrf.token_input()}
            <div class="form-group">
                <button id="setup-two-factor-btn" class="btn btn-primary">Set up two-factor authentication</button>
            </div>
        </form>
        </div>
    </div>
    %endif
</%def>

<%def name="what_is_it()">
    <div class="row">
        <div class="col-sm-12">
            <h3>What Is Two-Factor Authentication?</h3>
        </div>
    </div>
    <div class="row">
        <div class="col-sm-12">
            <p>
            <a href="https://support.aerofs.com/hc/en-us/articles/202610424">Two-factor authentication</a>
            adds additional security to your account.  In addition to your normal
            login, you'll need to enter a code that you get from an app on your
            phone or tablet when signing in to the AeroFS website or setting up new
            computers with AeroFS.
            </p>
        </div>
    </div>
</%def>

<%def name="setup()">
<h2>Two-Factor Authentication Setup</h2>

<div class="row">
    <div class="col-sm-12">
        <h4>Open your two-factor authentication app</h4>
        <p>You'll need to install a two-factor authentication application on
        your phone or tablet.  For more information, see our
        <a href="https://support.aerofs.com/hc/en-us/articles/202775400">support
        article on recommended two-factor authentication apps.</a></p>
    </div>
</div>
<div class="row">
    <div class="col-sm-12">
    <h4>Add AeroFS to the two-factor authentication app</h4>
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
</%def>