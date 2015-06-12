<%! from web.views.maintenance.maintenance_util import is_maintenance_mode %>
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
            non_maintenance_links = [
                ('status', _("Service status")),
                ('registered_apps', _("Registered apps")),
            ]

            links = [
                ('auditing', _("Auditing")),
                ('identity', _("Identity")),
                ('device_restriction', _("Device restriction")),
                ('timekeeping', _("Timekeeping")),
                ('collect_logs', _("Collect logs")),
                ('upgrade', _("Upgrade")),
                ('backup', _("Backup")),
                ('setup', _("Setup")),
            ]

            if is_maintenance_mode(None):
                links.append(('toggle_maintenance_mode', _("Exit maintenance mode")))
            else:
                links = non_maintenance_links + links
                links.append(('toggle_maintenance_mode', _("Enter maintenance mode")))
        %>
        <ul>
            %if is_maintenance_mode(None):
                %for link in non_maintenance_links:
                    <li class="text-muted">${link[1]}</li>
                %endfor
            %endif

            %for link in links:
                ${navigation.link(link)}
            %endfor
        </ul>

        <li class="nav-header">My AeroFS</li>
        <ul>
            ## Using this hack to refer to another service is not a best practice.
            ## However it has the lowest cost given the current architecture.
            <li><a href="javascript:goHome()">Home</a></li>
            <script>
                function goHome() {
                    window.location.assign("https://" + window.location.hostname);
                }
            </script>
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
