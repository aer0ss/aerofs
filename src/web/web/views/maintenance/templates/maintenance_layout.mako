<%inherit file="marketing_layout.mako"/>

<%namespace name="maintenance_mode" file="maintenance_mode.mako"/>
<%namespace name="error_message" file="maintenance_error_message.mako"/>
<%namespace name="navigation" file="navigation.mako"/>
<%namespace name="version" file="version.mako"/>
<%namespace name="no_ie" file="no_ie.mako"/>

<%no_ie:scripts/>

<%def name="home_url()">
    ${request.route_path('manage')}
</%def>

<%block name="top_navigation_bar_mobile">
    <%version:version_top_nav_item_mobile/>
</%block>

<%block name="top_navigation_bar_desktop">
    <%version:version_top_nav_item_desktop/>
</%block>

## N.B. dashboard_layout.mako uses the same layout
<div class="row">
    ## Left navigation bar
    <div class="span2 offset1">
        <ul class="nav nav-list left-nav">
            <li class="nav-header">My Appliance</li>
            <%
                links = [
                    # ('auditing', _("Auditing")),
                    ('status', _("Service Status")),
                    ('logs', _("Server Logs")),
                    ('registered_apps', _("Registered Apps")),
                    ('toggle_maintenance_mode', _("Maintenance")),
                    ('upgrade_appliance', _("Upgrade")),
                    ('backup_appliance', _("Backup")),
                    ('setup', _("Setup")),
                ]
            %>
            %for link in links:
                ${navigation.link(link)}
            %endfor

            <li class="nav-header">My AeroFS</li>
            ${navigation.link(('dashboard_home', 'Home'))}
        </ul>
    </div>

    ## Main body
    <div class="span8">
        <%maintenance_mode:alert/>
        ${next.body()}
    </div>
</div>

<%block name="layout_scripts">
    <%error_message:scripts/>
</%block>
