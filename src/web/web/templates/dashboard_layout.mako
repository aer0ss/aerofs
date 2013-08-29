## N.B. When updating this template, keep in mind that it is used in all
## deployment modes.

<%inherit file="base_layout.mako"/>

<%!
    from web.util import is_admin
%>

<div class="row">
    ## Vertical navigation bar
    <div class="span2 offset1">
        %if is_admin(request):
            ${render_left_navigation_for_admin()}
        %else:
            ${render_left_navigation_for_nonadmin()}
        %endif
    </div>

    ## Main body
    <div class="span8">
        ## pages that require navigation bars are bounded by "span8"
        ${next.body()}
    </div>
</div>

<%block name="top_navigation_bar">
    <li><a href="http://support.aerofs.com" target="_blank">Support</a></li>
    <li><a href="http://blog.aerofs.com" target="_blank">Blog</a></li>
    <li class="pull-right"><a href="${request.route_path('logout')}">Sign out</a></li>
    <li class="pull-right disabled"><a href="#">${request.session['username']}</a></li>
    % if is_admin(request):
        ${render_download_links(True)}
    % else:
        ${render_download_links(False)}
    % endif
</%block>

<%block name="home_url">
    ${request.route_path('dashboard_home')}
</%block>

<%def name="render_download_links(admin)">
    <li class="pull-right dropdown">
        <a class="dropdown-toggle" data-toggle="dropdown" href="#">
            ${render_download_text()} <b class="caret"></b>
        </a>
        <ul class="dropdown-menu">
            <li><a href="${request.route_path('download')}">
                AeroFS Desktop
            </a></li>

            %if request.registry.settings['deployment.mode'] == 'prod':
                <li><a href="http://play.google.com/store/apps/details?id=com.aerofs.android" target="_blank">
                    Android App
                </a></li>
            %endif

            %if admin:
                <li class="divider"></li>
                <li><a href="${request.route_path('download_team_server')}">
                    Team Server
                </a></li>
            %endif
        </ul>
    </li>
</%def>

<%def name="render_download_text()">
    Install
</%def>

<%def name="render_left_navigation_for_nonadmin()">
    <ul class="nav nav-list left-nav">
        ${render_nonadmin_links()}
    </ul>
</%def>

<%def name="render_left_navigation_for_admin()">
    <ul class="nav nav-list left-nav">
        <li class="nav-header">My AeroFS</li>
        ${render_nonadmin_links()}
        <li class="nav-header">My Team</li>
        ${render_admin_links()}
    </ul>
</%def>

<%def name="render_nonadmin_links()">
    <%
        links = [
            ('my_shared_folders', _("Shared Folders")),
            ('my_devices', _("Devices")),
            ('accept', _("Invitations")),
        ]
    %>
    % for link in links:
        ${render_navigation_link(link)}
    % endfor
</%def>

<%def name="render_admin_links()">
    <%
        links = [
            ('team_members', _("Team Members")),
            ('team_shared_folders', _("Shared Folders")),
            ('team_server_devices', _("Team Servers")),
            ('team_settings', _("Settings")),
        ]
    %>
    % for link in links:
        ${render_navigation_link(link)}
    % endfor
</%def>

## param link: tuple (route_name, text_to_display)
<%def name="render_navigation_link(link)">
    <li
        %if request.matched_route and request.matched_route.name == link[0]:
            class="active"
        %endif
    ><a href="${request.route_path(link[0])}">${link[1]}</a></li>
</%def>
