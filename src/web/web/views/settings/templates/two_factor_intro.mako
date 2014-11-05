<%inherit file="dashboard_layout.mako"/>
<%namespace name="components" file="two_factor_components.mako"/>
<%! page_title = "Enable Two-Factor Authentication" %>

%if two_factor_enforced:
    <div class="row alert alert-danger">
        <div class="col-sm-12">
            <h4>Watch out!</h4>
            <p>
            Setting up two-factor authentication again will invalidate your current
            device codes and backup codes.
            </p>
        </div>
    </div>
%endif

%if mandatory_tfa:
    <div class="page-block">
        <div class="row">
            <div class="col-sm-12">
                <p>
                Your organization's security policy requires that all users use <a href="https://support.aerofs.com/hc/en-us/articles/202610424">two-factor authentication</a> on their AeroFS account.
                <strong>You are able to use AeroFS because you have two-factor authentication enabled.</strong>
                </p>
            </div>
        </div>
    </div>
%endif

<div class="page-block">
    <%components:what_is_it/>
    <%components:how_it_works/>
</div>
