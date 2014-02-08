<%inherit file="marketing_layout.mako"/>

<%namespace name="maintenance_alert" file="maintenance_alert.mako"/>
<%namespace name="error_message" file="maintenance_error_message.mako"/>
<%namespace name="navigation" file="navigation.mako"/>

## N.B. dashboard_layout.mako uses the same layout
<div class="row">
    ## Left navigation bar
    <div class="span2 offset1">
        <ul class="nav nav-list left-nav">
            <li class="nav-header">Settings</li>
            <%
                links = [
                    ('registered_apps', _("Registered Apps")),
                    ('auditing', _("Auditing")),
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

            <li class="nav-header">My AeroFS</li>
            <a id="dashboard_home_link">Home</a>
        </ul>
    </div>

    ## Main body
    <div class="span8">
        <%maintenance_alert:html/>
        ${next.body()}
    </div>
</div>

<%block name="layout_scripts">
    <%error_message:scripts/>
    <script>
        $(document).ready(function() {
            $('#dashboard_home_link').attr('href', 'https://' + location.hostname);
        });
    </script>
</%block>