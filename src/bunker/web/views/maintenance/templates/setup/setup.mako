<%inherit file="../base_layout.mako"/>
<%! page_title = "Setup" %>

<%namespace name="maintenance_alert" file="../maintenance_alert.mako"/>
<%namespace name="error_message" file="../maintenance_error_message.mako"/>
<%namespace name="common" file="setup_common.mako"/>
<%namespace name="no_ie" file="../no_ie.mako"/>
<%namespace name="segment_io" file="../segment_io.mako"/>

<%def name="home_url()">
    ${request.route_path('maintenance_home')}
</%def>

<%block name="css">
    <link href="${request.static_path('web:static/css/compiled/aerofs-bunker.min.css')}" rel="stylesheet">
</%block>

<%block name="tracking_codes">
    ## This tracking code corresponds to the Private Cloud project in segment.io.
    ${segment_io.code('xtw6kl4cml')}
</%block>

<div class="col-sm-8 col-sm-offset-2">

    <%maintenance_alert:html/>

    %if page == 0:
        %if is_configuration_initialized:
            <%namespace name="license_page" file="license_page.mako"/>
            <%license_page:body/>
            <% local.page_scripts = license_page.scripts %>
        %elif restored_from_backup:
            <%namespace name="already_restored_page" file="already_restored_page.mako"/>
            <%already_restored_page:body/>
            <% local.page_scripts = already_restored_page.scripts %>
        %else:
            <%namespace name="create_or_restore_page" file="create_or_restore_page.mako"/>
            <%create_or_restore_page:body/>
            <% local.page_scripts = create_or_restore_page.scripts %>
        %endif
    %elif page == 1:
        <h3>Step 1 of 3</h3>
        <%namespace name="hostname_page" file="hostname_page.mako"/>
        <%hostname_page:body/>
        <% local.page_scripts = hostname_page.scripts %>
    %elif page == 2:
        <h3>Step 2 of 3</h3>
        <%namespace name="cert_page" file="cert_page.mako"/>
        <%cert_page:body/>
        <% local.page_scripts = cert_page.scripts %>
    %elif page == 3:
        <h3>Step 3 of 3</h3>
        <%namespace name="email_page" file="email_page.mako"/>
        <%email_page:body/>
        <% local.page_scripts = email_page.scripts %>
    %elif page == 4:
        <h3>Sit back and relax</h3>
        <%namespace name="apply_page" file="apply_page.mako"/>
        <%apply_page:body/>
        <% local.page_scripts = apply_page.scripts %>
    %endif
</div>

<%block name="scripts">
    <%no_ie:scripts/>
    <%error_message:scripts/>
    ${common.scripts(page)}
    ${local.page_scripts()}
</%block>
