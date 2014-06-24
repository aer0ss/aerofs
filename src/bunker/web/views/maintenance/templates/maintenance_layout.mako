<%inherit file="marketing_layout.mako"/>

<%namespace name="maintenance_alert" file="maintenance_alert.mako"/>
<%namespace name="error_message" file="maintenance_error_message.mako"/>
<%namespace name="navigation" file="navigation.mako"/>

## N.B. dashboard_layout.mako uses the same layout
## Left navigation bar
<div class="col-sm-3">
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
        <ul>
        %for link in links:
            ${navigation.link(link)}
        %endfor
        </ul>

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
        <ul>
            %for link in links:
                ${navigation.link(link)}
            %endfor
        </ul>

        <li class="nav-header">My AeroFS</li>
        <% import re %>
        ## remove the port number from the host name, if any.
        <ul>
            <li><a href="https://${re.sub(r':.*$', '', request.host)}">Home</a></li>
        </ul>
    </ul>
</div>

## Main body
<div class="col-sm-9">
    <%maintenance_alert:html/>
    ${next.body()}
</div>

<%block name="layout_scripts">
    <%error_message:scripts/>
</%block>
