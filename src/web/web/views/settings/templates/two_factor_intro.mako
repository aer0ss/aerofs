<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Enable two-factor Authentication" %>

% if enforcing:
<div class="row alert alert-danger">
    <div class="col-sm-12">
        <h4>Watch out!</h4>
        <p>
        Setting up two-factor authentication again will invalidate your current
        device codes and backup codes.
        </p>
    </div>
</div>
% endif

<h2>Two-Factor Authentication</h2>
<div class="row">
    <div class="col-sm-12">
        <p>
        <a href="https://support.aerofs.com/hc/en-us/articles/202775400">Two-factor authentication</a>
        adds additional security to your account.  In addition to your normal
        login, you'll need to enter a code that you get from an app on your
        phone or tablet when signing in to the AeroFS website or setting up new
        computers with AeroFS.
        </p>
    </div>
</div>

<h3>Using Two-Factor Authentication</h3>
<div class="row">
    <div class="col-sm-4">
        <h4>Log in as normal</h4>
        ## Should this text change depending on the value of lib.authenticator?
        ## It might be a liar for external users in the openid/password combo world...
        <p>Enter your username and password as normal.</p>
    </div>
    <div class="col-sm-4">
        <h4>Enter code from phone</h4>
        <p>You'll be prompted to enter an additional six-digit code that you'll
        read from your mobile device to verify your identity.</p>
    </div>
    <div class="col-sm-4">
        <h4>You're logged in</h4>
        <p>Use AeroFS as normal.</p>
    </div>
</div>
<div class="row">
    <div class="col-sm-4 col-sm-offset-4 centered">
    <form method="post" class="form-inline" role="form" action="${request.route_path('two_factor_setup')}">
        ${self.csrf.token_input()}
        <div class="form-group">
            <button id="setup-two-factor-btn" class="btn btn-primary">Set up two-factor authentication</button>
        </div>
    </form>
    </div>
</div>
