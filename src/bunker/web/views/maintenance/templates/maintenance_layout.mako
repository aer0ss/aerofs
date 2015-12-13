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
            non_maintenance_links = ['status','registered_apps']

            # Please make alphabetical by display name.
            links = [
                ('auditing', _("Auditing")),
                ('backup_and_upgrade', _("Backup and upgrade")),
                ('collect_logs', _("Collect logs")),
                ('customization', _("Customization")),
                ('device_restriction', _("Device restriction")),
                ('email_integration', _("Email integration")),
                ('identity', _("Identity")),
                ('link_settings', _("Link Sharing")),
                ('password_restriction', _("Password restriction")),
                ('registered_apps', _("Registered apps")),
                ('status', _("Service status")),
                ('session_management', _("Session management")),
                ('setup', _("Setup")),
                ('sync_settings', _("Sync settings")),
                ('toggle_maintenance_mode', _("System maintenance")),
                ('timekeeping', _("Timekeeping")),
            ]
        %>
        <ul>
            %for link in links:
                %if ( is_maintenance_mode(None) and link[0] in non_maintenance_links):
                    <li class="text-muted">${link[1]}</li>
                %else:
                    ${navigation.link(link)}
                %endif
            %endfor
        </ul>

        <li class="nav-header">My AeroFS</li>
        <ul>
            ## Using this hack to refer to another service is not a best practice.
            ## However it has the lowest cost given the current architecture.
            <li class="nav-link"><a href="javascript:goHome()">Home</a></li>
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
