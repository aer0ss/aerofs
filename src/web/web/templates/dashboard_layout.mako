## N.B. When updating this template, keep in mind that it is used in all
## deployment modes.

<%inherit file="base_layout.mako"/>
<%namespace name="navigation" file="navigation.mako"/>

<%!
    from web.auth import is_admin
    from pyramid.security import authenticated_userid
    from web.util import str2bool
    from web.util import get_folder_invitation_count
%>

<div class="row">
    ## Navigation bar
    ## Gets subsumed by top menu on mobile
    <div class="col-sm-3 hidden-xs">
        %if is_admin(request):
            ${render_navigation_for_admin()}
        %else:
            ${render_navigation_for_nonadmin()}
        %endif
        ${render_user_invite()}
    </div>

    ## Main body
    <div class="col-sm-9">
        ${next.body()}
    </div>
</div>

<%block name="top_navigation_bar_mobile">
    <div class="visible-xs">
        <div class="btn-group pull-right">
            <a href="#" class="btn btn-default dropdown-toggle"
                    data-toggle="dropdown">
                <span class="glyphicon glyphicon-th-list"></span> Menu
            </a>
            <ul class="dropdown-menu">
                <%navigation:marketing_links/>
                <li class="divider"></li>
                <li><a href="${request.route_path('access_tokens')}">My apps</a></li>
                <li><a href="${request.route_path('settings')}">Settings</a></li>
                <li><a href="${request.route_path('logout')}">Sign out</a></li>
                <li class="divider"></li>
                ## Navigation bar
                %if is_admin(request):
                    ${render_navigation_for_admin()}
                %else:
                    ${render_navigation_for_nonadmin()}
                %endif
            </ul>
        </div>
        <div class="btn-group pull-right">
            <a href="#" class="btn btn-default dropdown-toggle" data-toggle="dropdown">
                Install <b class="caret"></b>
            </a>
            % if is_admin(request):
                ${render_download_links(True)}
            % else:
                ${render_download_links(False)}
            % endif
        </div>
    </div>
</%block>

<%block name="top_navigation_bar_tablet">
    <div class="hidden-lg hidden-xs">
        <div class="btn-group pull-right">
            <a href="#" class="btn btn-default dropdown-toggle"
                    data-toggle="dropdown">
                <span class="glyphicon glyphicon-th-list"></span> Menu
            </a>
            <ul class="dropdown-menu">
                <%navigation:marketing_links/>
                <li class="divider"></li>
                <li><a href="${request.route_path('access_tokens')}">My apps</a></li>
                <li><a href="${request.route_path('settings')}">Settings</a></li>
                <li><a href="${request.route_path('logout')}">Sign out</a></li>
            </ul>
        </div>
        <div class="btn-group pull-right">
            <a href="#" class="btn btn-default dropdown-toggle" data-toggle="dropdown">
                ${render_download_text()} <b class="caret"></b>
            </a>
            % if is_admin(request):
                ${render_download_links(True)}
            % else:
                ${render_download_links(False)}
            % endif
        </div>
    </div>
</%block>

<%block name="top_navigation_bar_desktop">
        <%navigation:marketing_links/>
        <li class="pull-right dropdown visible-lg">
            <a class="dropdown-toggle" data-toggle="dropdown" href="#">
                ${authenticated_userid(request)} <b class="caret"></b>
            </a>
            <ul class="dropdown-menu">
                ## Remember to update top_navigation_bar_mobile() when adding items
                <li><a href="${request.route_path('access_tokens')}">My apps</a></li>
                <li><a href="${request.route_path('settings')}">Settings</a></li>
                <li><a href="${request.route_path('logout')}">Sign out</a></li>
            </ul>
        <li class="pull-right dropdown visible-lg">
            <a class="dropdown-toggle" data-toggle="dropdown" href="#">
                ${render_download_text()} <b class="caret"></b>
            </a>
            % if is_admin(request):
                ${render_download_links(True)}
            % else:
                ${render_download_links(False)}
            % endif
        </li>
</%block>

<%block name="custom_banner_display">
    <%
        from web.util import escape_html_except_anchors
        banner_text = request.registry.settings.get('customization.banner_text')
    %>
    %if banner_text:
        <div id="flash-msg-info" class="alert alert-block" style="background:#EEE">
            <span id="flash-msg-info-body">${ banner_text | n,escape_html_except_anchors }</span>
        </div>
    %endif
</%block>

<%def name="home_url()">
    ${request.route_path('dashboard_home')}
</%def>

<%def name="render_download_links(admin)">
    <ul class="dropdown-menu">
        <li><a href="${request.route_path('download')}">
            AeroFS desktop
        </a></li>

        <%
            settings = request.registry.settings
            prop = 'web.disable_download_mobile_client'

            ## the intended default behaviour is to _display_ the item
            ## so this value is true iff everything points to true
            disable_download_mobile_client = prop in settings and str2bool(settings[prop])
        %>
        %if not disable_download_mobile_client:
            <li><a href="${request.route_path('add_mobile_device')}">
                Mobile apps
            </a></li>
        %endif

        %if admin:
            <li><a href="${request.route_path('download_team_server')}">
                Team server
            </a></li>
        %endif
    </ul>
</%def>

<%def name="render_download_text()">
    Install
</%def>

<%def name="render_navigation_for_nonadmin()">
    <ul class="nav nav-list left-nav">
        <li class="nav-header">My AeroFS</li>
        <ul>
            ${render_nonadmin_links()}
        </ul>
        <li class="nav-header">My organization</li>
        <ul>
            ${render_org_links()}
        </ul>
    </ul>
</%def>

<%def name="render_navigation_for_admin()">
    <ul class="nav nav-list left-nav">
        <li class="nav-header">My AeroFS</li>
        <ul>
            ${render_nonadmin_links()}
        </ul>
        <li class="nav-header">My organization</li>
        <ul>
            ${render_org_links()}
            ${render_admin_org_links()}
        </ul>
        <li class="nav-header">My deployment</li>
        <ul>
            <%
                mng_url = 'https://' + str(request.registry.settings['base.host.unified']) + '/admin'
            %>
            <li><a href="${mng_url}" target="_blank">Manage appliance</a></li>

            <%
                links = [
                    ('licensing', _("Licensing")),
                    ('download_sccm', _("Enterprise deployment"))
                ]
            %>
            % for link in links:
                ${navigation.link(link)}
            % endfor

        </ul>
    </ul>
</%def>
<%def name="render_nonadmin_links()">
    <%
        links = []
        links.append(('files', _("My files")))
        links.append(('my_shared_folders', _("Manage shared folders")))

        pending_count = get_folder_invitation_count(request)
        if pending_count:
            links.append(('accept', _("Pending invitations"), _(str(pending_count))))
        else:
            links.append(('accept', _("Pending invitations")))

        links.append(('my_devices', _("My devices")))
    %>
    % for link in links:
        ${navigation.link(link)}
    % endfor
</%def>

<%def name="render_org_links()">
    <%
        links = [
            ('org_users', _("Users")),
            ('org_groups', _("Groups"))
        ]
    %>
    % for link in links:
        ${navigation.link(link)}
    % endfor
</%def>

<%def name="render_admin_org_links()">
    <%
        links = [
            ('org_shared_folders', _("Shared folders")),
            ('team_server_devices', _("Team servers")),
            ('org_settings', _("Settings"))
        ]
    %>
    % for link in links:
        ${navigation.link(link)}
    % endfor
</%def>

<%def name="render_user_invite()">
    <div class="well">
    <p><strong>Invite a coworker to AeroFS:</strong></p>
    <form id="invite-coworker" class="form">
        <input type="text" class="form-control" id="invite-coworker-email" placeHolder="Email address"/><br/>
        <button id="invite-coworker-submit" class="btn btn-primary" type="submit">Send invite</button>
    </form>
    </div>
</%def>

<%block name="layout_scripts">
    <script type="text/javascript">
        $("#invite-coworker").submit(function() {
            var emailAddress = $('#invite-coworker-email').val();
            jQuery.ajax({
                'type': 'POST',
                'url': '/users/invite',
                'contentType': 'application/json',
                'data': JSON.stringify({'user': emailAddress}),
                'dataType': 'json',
                'error': showErrorMessageFromResponse,
                'success': function (data, textStatus, xhr) {
                    if (xhr.status == 200) {
                        showSuccessMessage('Successfully invited ' + emailAddress);
                        $('#invite-coworker-email').val('');
                    }
                }
            });
            return false;
        });
    </script>
</%block>
