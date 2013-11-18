<%inherit file="base_layout.mako"/>
<%! page_title = "Setup" %>

<%namespace name="common" file="setup/common.mako"/>
<%namespace name="no_ie" file="no_ie.mako"/>

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

<div class="span8 offset2">
    %if page == 0:
        ## Page 0 must be the license page. See setup_view.py:_setup_common()
        <%namespace name="license_page" file="setup/license_page.mako"/>
        <%namespace name="license_authorized_page" file="setup/license_authorized_page.mako"/>
        ## See the logic in setup_view.py:setup()
        %if is_license_present_and_valid:
            <%license_authorized_page:body/>
            <% local.page_scripts = license_authorized_page.scripts %>
        %else:
            <%license_page:body/>
            <% local.page_scripts = license_page.scripts %>
        %endif
    %elif page == 1:
        <h3>Step 1 of 4</h3>
        <%namespace name="hostname_page" file="setup/hostname_page.mako"/>
        <%hostname_page:body/>
        <% local.page_scripts = hostname_page.scripts %>
    %elif page == 2:
        <h3>Step 2 of 4</h3>
        <%namespace name="identity_page" file="setup/identity_page.mako"/>
        <%identity_page:body/>
        <% local.page_scripts = identity_page.scripts %>
    %elif page == 3:
        <h3>Step 3 of 4</h3>
        <%namespace name="email_page" file="setup/email_page.mako"/>
        <%email_page:body/>
        <% local.page_scripts = email_page.scripts %>
    %elif page == 4:
        <h3>Step 4 of 4</h3>
        <%namespace name="cert_page" file="setup/cert_page.mako"/>
        <%cert_page:body/>
        <% local.page_scripts = cert_page.scripts %>
    %elif page == 5:
        <h3>Sit back and relax</h3>
        <%namespace name="apply_page" file="setup/apply_and_create_user_page.mako"/>
        <%apply_page:body/>
        <% local.page_scripts = apply_page.scripts %>
    %endif
</div>

<%block name="scripts">
    <%no_ie:scripts/>
    ${common.scripts(page)}
    ${local.page_scripts()}
</%block>
