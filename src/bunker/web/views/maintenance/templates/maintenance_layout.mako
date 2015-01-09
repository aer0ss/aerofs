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
                ('device_restriction', _("Device Restriction")),
                ('timekeeping', _("Timekeeping")),
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
                ('collect_logs', _("Collect Logs")),
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
