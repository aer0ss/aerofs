## N.B. When updating this template, keep in mind that it is used in all
## deployment modes.

<%inherit file="base_layout.mako"/>
<%namespace name="navigation" file="navigation.mako"/>

<%!
    from web.auth import is_admin
    from pyramid.security import authenticated_userid
    from web.util import str2bool, is_user_view_enabled_nonadmin, is_group_view_enabled_nonadmin
    from web.util import get_folder_invitation_count, get_days_until_license_expires
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
    <div class="visible-xs mobile">
        <div class="btn-group pull-right">
            <a href="#" class="btn btn-default dropdown-toggle"
                    data-toggle="dropdown">
                <span class="glyphicon glyphicon-th-list"></span> Menu
            </a>
            <ul class="dropdown-menu">
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
    <div class="hidden-lg hidden-xs tablet">
        <div class="btn-group pull-right">
            <a href="#" class="btn btn-default dropdown-toggle"
                    data-toggle="dropdown">
                <span class="glyphicon glyphicon-th-list"></span> Menu
            </a>
            <ul class="dropdown-menu">
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
    <li class="pull-right dropdown visible-lg nav-link">
        <a class="dropdown-toggle" data-toggle="dropdown" href="#">
            ${authenticated_userid(request)} <b class="caret"></b>
        </a>
        <ul class="dropdown-menu">
            ## Remember to update top_navigation_bar_mobile() when adding items
            <li><a href="${request.route_path('access_tokens')}">My apps</a></li>
            <li><a href="${request.route_path('settings')}">Settings</a></li>
            <li><a href="${request.route_path('logout')}">Sign out</a></li>
        </ul>
    </li>
    <li class="pull-right dropdown visible-lg nav-link">
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

<%block name="banner">
    <%
        days = get_days_until_license_expires(request.registry.settings)
        show_license_warning = is_admin and days <= 30
    %>
    % if show_license_warning:
        <% day_string = 'day' if days == 1 else 'days'%>
        <%
            state = 'alert-info'
            if days <= 7:
                state = 'alert-danger'
        %>

        <div id="license-expires-banner" class="top-banner ${ state }">
            <p class="message">
                % if days > 0:
                    Your license expires in <strong>${ days } ${ day_string }</strong>.
                % elif days == 0:
                    Your license expires <strong>today</strong>.
                % else:
                    Your license has expired.
                % endif
                Please log-in to your <a href="https://enterprise.aerofs.com/" target="_blank">
                account dashboard </a> to update your license, or <a href="mailto:support@aerofs.com">
                contact support</a> for help.
            </p>
            <span class="close glyphicon glyphicon-remove"></span>
        </div>
    % endif

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
        ${render_my_org_for_nonadmin()}
    </ul>
</%def>

<%def name="render_my_org_for_nonadmin()">
    <%
        settings    = request.registry.settings
        show_users  = is_user_view_enabled_nonadmin(settings)
        show_groups = is_group_view_enabled_nonadmin(settings)
    %>

    %if show_users or show_groups:
        <li class="nav-header">My organization</li>
        <ul>
            ${render_org_links(show_users, show_groups)}
        </ul>
    %endif
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
            <li class="nav-link"><a href="${mng_url}" target="_blank">Manage appliance</a></li>

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

<%def name="render_org_links(show_users=True, show_groups=True)">
    <%
        links = []
        if show_users:
            links.append(('org_users', _("Users")))
        if show_groups:
            links.append(('org_groups', _("Groups")))
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
    <p><strong>Invite a coworker to AeroFS</strong></p>
    <form id="invite-coworker" class="form">
        <input type="text" class="form-control" id="invite-coworker-email" placeHolder="Email address"/><br/>
        <button id="invite-coworker-submit" class="btn btn-primary" type="submit">Send invite</button>
    </form>
    </div>
</%def>

<%block name="footer">
    <footer>
        <div class="container">
            <div class="row">
                <div class="col-sm-12" id="footer-span">
                    <ul class="list-inline">
                        <li><a href="https://www.aerofs.com/about">About</a></li>
                        <li><a href="https://www.aerofs.com/press">Press</a></li>
                        <li><a href="http://www.aerofs.com/blog">Blog</a></li>
                        <li><a href="http://www.twitter.com/aerofs">Twitter</a></li>
                        <li><a href="https://www.aerofs.com/careers">Careers</a></li>
                        <li><a href="https://www.aerofs.com/terms">Terms</a></li>
                        <li><a href="http://support.aerofs.com">Support</a></li>
                        <li class="pull-right">&copy; Air Computing Inc. 2016</li>
                    </ul>
                </div>
            </div>
        </div>
    </footer>
</%block>

<%block name="layout_scripts">
    <script type="text/javascript">
        $(document).ready(function () {
            var cookie = document.cookie;
            if (cookie.indexOf("license_modal_closed") == -1) {
                $('.top-banner').show();
            }
        });

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

        $(".top-banner .close").on('click', function () {

            // Show them the message every day, but if they close out,
            // don't show them more than once in the same day
            var date = new Date();
            date.setHours(24,0,0,0);

            document.cookie = "license_modal_closed=true; expires=" + date.toUTCString() + "; path=/";

            $(this).parent().hide();
        });
    </script>
</%block>
