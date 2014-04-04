<%inherit file="marketing_layout.mako"/>

<%namespace name="maintenance_alert" file="maintenance_alert.mako"/>
<%namespace name="error_message" file="maintenance_error_message.mako"/>
<%namespace name="navigation" file="navigation.mako"/>

## N.B. dashboard_layout.mako uses the same layout
## Left navigation bar
<div class="span2 offset1">
    <ul class="nav nav-list left-nav">
        <li class="nav-header">Settings</li>
        <%
            links = [
                ('registered_apps', _("Registered Apps")),
                ('auditing', _("Auditing")),
                ('identity', _("Identity")),
                ('setup', _("Setup")),
            ]
        %>
        %for link in links:
            ${navigation.link(link)}
        %endfor

        <li class="nav-header">Maintenance</li>
        <%
            links = [
                ('toggle_maintenance_mode', _("Toggle Mode")),
                ('status', _("Service Status")),
                ('logs', _("Server Logs")),
                ('upgrade', _("Upgrade")),
                ('backup', _("Backup")),
            ]
        %>
        %for link in links:
            ${navigation.link(link)}
        %endfor

        <%
            # update the value for development, remove the whole check before release
            enable_dryad = False
        %>
        %if enable_dryad:
            <li class="nav-header">Help</li>
            ${navigation.link(('report-problem', _("Report a Problem")))}
            <li><a href="http://support.aerofs.com">Support</a></li>
        %endif

        <li class="nav-header">My AeroFS</li>
        <% import re %>
        ## remove the port number from the host name, if any.
        <a href="https://${re.sub(r':.*$', '', request.host)}">Home</a>
    </ul>
</div>

## Main body
<div class="span8">
    <%maintenance_alert:html/>
    ${next.body()}
</div>

<%block name="layout_scripts">
    <%error_message:scripts/>
</%block>
