## N.B. When updating this template, keep in mind that it is used in all
## deployment modes.

<%inherit file="base_layout.mako"/>
<%namespace name="navigation" file="navigation.mako"/>

<%!
    from web.auth import is_admin
    from pyramid.security import  authenticated_userid
    from web.util import is_private_deployment, str2bool
%>

## N.B. maintenance_layout.mako uses the same layout
<div class="row">
    ## Left navigation bar
    <div class="span2 offset1">
        %if is_admin(request):
            ${render_left_navigation_for_admin()}
        %else:
            ${render_left_navigation_for_nonadmin()}
        %endif
    </div>

    ## Main body
    <div class="span8">
        ${next.body()}
    </div>
</div>

<%block name="top_navigation_bar_mobile">
    <%navigation:marketing_links/>
    <li class="divider"></li>
    %if is_private_deployment(request.registry.settings):
        <li><a href="${request.route_path('access_tokens')}">My Apps</a></li>
    %endif
    <li><a href="${request.route_path('settings')}">Settings</a></li>
    <li><a href="${request.route_path('logout')}">Sign out</a></li>
</%block>

<%block name="top_navigation_bar_desktop">
    <%navigation:marketing_links/>
    <li class="pull-right dropdown">
        <a class="dropdown-toggle" data-toggle="dropdown" href="#">
            ${authenticated_userid(request)} <b class="caret"></b>
        </a>
        <ul class="dropdown-menu">
            ## Remember to update top_navigation_bar_mobile() when adding items
            %if is_private_deployment(request.registry.settings):
                <li><a href="${request.route_path('access_tokens')}">My Apps</a></li>
            %endif
            <li><a href="${request.route_path('settings')}">Settings</a></li>
            <li><a href="${request.route_path('logout')}">Sign out</a></li>
        </ul>
    </li>

    % if is_admin(request):
        ${render_download_links(True)}
    % else:
        ${render_download_links(False)}
    % endif
</%block>

<%def name="home_url()">
    ${request.route_path('dashboard_home')}
</%def>

<%def name="render_download_links(admin)">
    <li class="pull-right dropdown">
        <a class="dropdown-toggle" data-toggle="dropdown" href="#">
            ${render_download_text()} <b class="caret"></b>
        </a>
        <ul class="dropdown-menu">
            <li><a href="${request.route_path('download')}">
                AeroFS Desktop
            </a></li>

            <%
                settings = request.registry.settings
                prop = 'web.disable_download_mobile_client'

                ## the intended default behaviour is to _display_ the item
                ## so this value is true iff everything points to true
                disable_download_mobile_client = \
                    is_private_deployment(settings) \
                    and prop in settings \
                    and str2bool(settings[prop])
            %>
            %if not disable_download_mobile_client:
                <li><a href="${request.route_path('add_mobile_device')}">
                    Mobile Apps
                </a></li>
            %endif

            %if admin:
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
        <li class="nav-header">My Organization</li>
        ${render_admin_org_links()}
        %if is_private_deployment(request.registry.settings):
            <li class="nav-header">My Appliance</li>

            <% import re %>
            ## remove the port number from the host name, if any.
            <li><a href="http://${re.sub(r':.*$', '', request.host) + ':8484'}">Manage</a></li>
        %endif
    </ul>
</%def>

<%def name="render_nonadmin_links()">
    <%
        links = []
        if is_private_deployment(request.registry.settings):
            links.append(('files', _("My Files")))
        links.append(('my_shared_folders', _("My Shared Folders")))
        links.append(('accept', _("My Invitations")))
        links.append(('my_devices', _("My Devices")))
    %>
    % for link in links:
        ${navigation.link(link)}
    % endfor
</%def>

<%def name="render_admin_org_links()">
    <%
        links = [
            ('org_users', _("Users")),
            ('org_shared_folders', _("Shared Folders")),
            ('team_server_devices', _("Team Servers")),
            ('org_settings', _("Settings")),
        ]
    %>
    % for link in links:
        ${navigation.link(link)}
    % endfor
</%def>
