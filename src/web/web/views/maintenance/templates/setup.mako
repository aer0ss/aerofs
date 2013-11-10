<%inherit file="base_layout.mako"/>
<%! page_title = "Setup" %>

<%block name="home_url">
    ${request.route_path('dashboard_home')}
</%block>

<%block name="css">
    <style type="text/css">
        ## For footnotes under main options
        .main-option-footnote {
            margin-top: 8px;
            font-size: small;
        }
        ## For footnotes under input boxes
        .input-footnote {
            margin-top: -8px;
            margin-bottom: 8px;
            font-size: small;
        }
        .small-modal {
            top: 150px;
            width: 440px;
            margin-left: -220px;
        }
    </style>
</%block>

## TODO (WW) This is a hack to get around the fact that JS in individual setup
## pages can't be included *after* base_layout.mako includes jQuery. A proper
## fix is to change how these pages are included/inherited to allow standard
## <%block name="script">'s to be defined here.
<script src="${request.static_path('web:static/js/jquery.min.js')}"></script>

<div class="span8 offset2">
    %if page == 0:
        ## Page 0 must be the license page. See setup_view.py:_setup_common()
        <%namespace name="license_page" file="setup/license_page.mako"/>
        <%namespace name="license_authorized_page" file="setup/license_authorized_page.mako"/>
        ## See the logic in setup_view.py:setup()
        %if is_license_present_and_valid:
            <%license_authorized_page:body/>
        %else:
            <%license_page:body/>
        %endif
    %elif page == 1:
        <h3>Step 1 of 4</h3>
        <%namespace name="hostname_page" file="setup/hostname_page.mako"/>
        <%hostname_page:body/>
    %elif page == 2:
        <h3>Step 2 of 4</h3>
        <%namespace name="identity_page" file="setup/identity_page.mako"/>
        <%identity_page:body/>
    %elif page == 3:
        <h3>Step 3 of 4</h3>
        <%namespace name="email_page" file="setup/email_page.mako"/>
        <%email_page:body/>
    %elif page == 4:
        <h3>Step 4 of 4</h3>
        <%namespace name="cert_page" file="setup/cert_page.mako"/>
        <%cert_page:body/>
    %elif page == 5:
        <h3>Sit back and relax</h3>
        <%namespace name="apply_page" file="setup/apply_and_create_user_page.mako"/>
        <%apply_page:body/>
    %endif
</div>

<%namespace name="common" file="setup/common.mako"/>

<%block name="scripts">
    ${common.scripts(page)}
</%block>
