<%inherit file="marketing_layout.mako"/>
<%namespace name="components" file="two_factor_components.mako"/>
<%! page_title = "Enable Two-Factor Authentication" %>

<div class="row">
    <div class="col-sm-8 col-sm-offset-2">
        <div class="page-block">
            <div class="row">
                <div class="col-sm-12">
                    <h1>Please Activate Two-Factor Authentication</h1>
                </div>
            </div>
            <div class="row">
                <div class="col-sm-3 hidden-xs">
                    <img src="${request.static_path('web:static/img/2fa-phone.png')}" class="img-responsive">
                </div>
                <div class="col-sm-9">
                    <p>
                    Your organization's security policy requires that all users use <a href="https://support.aerofs.com/hc/en-us/articles/202610424">two-factor authentication</a> on their AeroFS account.
                        <strong>You will not be able to use AeroFS until you activate two-factor authentication using your phone or tablet.</strong>
                    </p>
                    <form method="post" class="form-inline" role="form"
                            action="${request.route_path('two_factor_setup')}">
                        ${self.csrf.token_input()}
                        <div class="form-group">
                            <button id="setup-two-factor-btn" class="btn btn-primary btn-lg">Set up two-factor authentication now</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
        <div class="page-block">
            <div class="row">
                <div class="col-md-12">
                    <h4>Related articles</h4>
                    <p><a href="https://support.aerofs.com/hc/en-us/articles/202610424">What is two-factor authentication and how do I set it up?</a></p>
                    <p><a href="https://support.aerofs.com/hc/en-us/articles/203625510">How do I log in with two-factor authentication?</a></p>
                    <p><a href="https://support.aerofs.com/hc/en-us/articles/202775400">What apps can I use on my phone or tablet for two-factor authentication?</a></p>
                </div>
            </div>
        </div>
    </div>
</div>