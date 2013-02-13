<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>AeroFS</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="shortcut icon" href="${request.static_url('aerofs_web.layout:static/favicon.ico')}">

    ## stylesheets
    <link href='https://fonts.googleapis.com/css?family=Open+Sans:300,400,600' rel='stylesheet' type='text/css'>
    <link href="${request.static_url('aerofs_web.layout:static/css/bootstrap.css')}" rel="stylesheet">
    <link href="${request.static_url('aerofs_web.layout:static/css/responsive.css')}" rel="stylesheet">
    <link href="${request.static_url('aerofs_web.layout:static/css/main.css')}" rel="stylesheet">
    <%block name="css"/>

    ## Le HTML5 shim, for IE6-8 support of HTML5 elements
    <!--[if lt IE 9]>
    <script src="https://html5shim.googlecode.com/svn/trunk/html5.js"></script>
    <![endif]-->

    ## fav and touch icons
    <link rel="shortcut icon" href="http://www.aerofs.com/img/favicon.ico">

    ## Google Analytics. Put it to header rather than footer: http://stackoverflow.com/questions/10712908/google-analytics-in-header-or-footer
    ## TODO (WW) use different API keys for prod and dev as Mixpanel does?
    <script type="text/javascript">
        var _gaq = _gaq || [];
        _gaq.push(['_setAccount', 'UA-24554389-1']);
        _gaq.push(['_trackPageview']);
        _gaq.push(['_trackPageLoadTime']);

        (function() {
            var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
            ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
            var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
        })();
    </script>

    ## Mixpanel. Put this to header rather than footer as required by Mixpanel.
    <script type="text/javascript">(function(e,b){if(!b.__SV){var a,f,i,g;window.mixpanel=b;a=e.createElement("script");a.type="text/javascript";a.async=!0;a.src=("https:"===e.location.protocol?"https:":"http:")+'//cdn.mxpnl.com/libs/mixpanel-2.2.min.js';f=e.getElementsByTagName("script")[0];f.parentNode.insertBefore(a,f);b._i=[];b.init=function(a,e,d){function f(b,h){var a=h.split(".");2==a.length&&(b=b[a[0]],h=a[1]);b[h]=function(){b.push([h].concat(Array.prototype.slice.call(arguments,0)))}}var c=b;"undefined"!==
        typeof d?c=b[d]=[]:d="mixpanel";c.people=c.people||[];c.toString=function(b){var a="mixpanel";"mixpanel"!==d&&(a+="."+d);b||(a+=" (stub)");return a};c.people.toString=function(){return c.toString(1)+".people (stub)"};i="disable track track_pageview track_links track_forms register register_once alias unregister identify name_tag set_config people.set people.increment people.append people.track_charge people.clear_charges people.delete_user".split(" ");for(g=0;g<i.length;g++)f(c,i[g]);b._i.push([a,
        e,d])};b.__SV=1.2}})(document,window.mixpanel||[]);
        mixpanel.init("${request.registry.settings['mixpanel.api_key']}");

        %if 'username' in request.session:
            mixpanel.identify("${request.session['username']}");
        %endif
    </script>

</head>

<body>

## this wrapper is used to keep the footer at the bottom, even if the content height is less than the window height ("sticky" footer)
<div id="wrapper">

    <div class="container">
        <div id="empty_message_bar" class="offset5 message_container">View Message</div>
    </div>
    <div class="container">
        <div id="message_bar" class="span6 offset3 message_container">
            % if request.session.peek_flash(queue='error_queue'):
                % for message in request.session.pop_flash(queue='error_queue'):
                    <div class="flash_message error_message">
                        ${message}
                    </div>
                % endfor
            % endif
            % if request.session.peek_flash(queue='success_queue'):
                % for message in request.session.pop_flash(queue='success_queue'):
                    <div class="flash_message success_message">
                        ${message}
                    </div>
                % endfor
            % endif
        </div>
    </div>

<%!
    ## Set navigation_bars = True in renderer pages to enable top and left
    ## navigation bars. If navigation bars are enabled, the entire content of
    ## the renderer page is wrapped by a "span8" div. Otherwise, the content is
    ## wrapped by a top-level "row" div.
    ##
    navigation_bars = False

    from aerofs_web.helper_functions import is_admin
%>

    <div class="navbar">
        <div class="navbar-inner">
            <div class="container">
                <div class="row">
                    <div class="span10 offset1">
                        <a class="brand" href="/">
                            <img src="${request.static_url('aerofs_web.layout:static/img/aerofs-logo-navbar.png')}" width="151" height="44" alt="AeroFS" />
                        </a>

                        %if 'username' in request.session and \
                                self.attr.navigation_bars is True:
                            ${render_top_right_navigation()}
                        %endif
                    </div>
                </div>
            </div>
        </div>
    </div>

    <div class="container">
        <div class="row">
            %if 'username' in request.session and \
                    self.attr.navigation_bars is True:
                <div class="span2 offset1">
                    %if is_admin(request):
                        ${render_left_navigation_for_admin()}
                    %else:
                        ${render_left_navigation_for_nonadmin()}
                    %endif
                </div>
                <div class="span8">
                    ## pages that require navigation bars are bounded by "span8"
                    ${next.body()}
                </div>
            %else:
                ## pages that don't require navigation bars are bounded by "row"
                ${next.body()}
            %endif
        </div>
    </div>

    ## this element is the same height as the footer, and it ensures that the footer never overlaps the content
    <div id="footer-push"></div>
</div>

<%def name="render_top_right_navigation()">

    ## TODO (WW) CSS for navbar buttons is completely broken.

    <div class="pull-right" style="margin-top: 2em;">
        % if is_admin(request):
            ${render_download_link_for_admin()}
        % else:
            ${render_download_link_for_nonadmin()}
        % endif

        <span class="btn-group" style="margin-left: 3em; padding-bottom: 8px;">
            <a class="dropdown-toggle" data-toggle="dropdown" href="#">
                ${request.session['username']} &#9662;
                ## The result of the following line is ugly. The CSS for navgar
                ## buttons has been messed up.
                ## <span class="caret"></span>
            </a>
            <ul class="dropdown-menu">
                <!-- <li><a href="#">Profile</a></li>
                <li class="divider"></li> -->
                <li><a href="${request.route_path('logout')}">Sign Out</a></li>
            </ul>
        </span>
    </div>

</%def>

<%def name="render_download_link_for_nonadmin()">
    <a href="https://www.aerofs.com/download" target="_blank">
        ${render_download_text()}
    </a>
</%def>

<%def name="render_download_link_for_admin()">
    <span class="btn-group" style="margin-left: 3em; padding-bottom: 8px;">
        <a class="dropdown-toggle" data-toggle="dropdown" href="#">
            ## see the previous occurrance of the UNICODE char for comments
            ${render_download_text()} &#9662;
        </a>
        <ul class="dropdown-menu">
            <li><a href="https://www.aerofs.com/download" target="_blank">
                Desktop Client
            </a></li>
            <li><a href="${request.route_path('install_team_server')}">
                Team Server
            </a></li>
        </ul>
    </span>
</%def>

<%def name="render_download_text()">
    Install
</%def>

<%def name="render_left_navigation_for_nonadmin()">
    <ul class="nav nav-list">
        ${render_nonadmin_links()}
        ${render_help_links()}
    </ul>
</%def>

<%def name="render_left_navigation_for_admin()">
    <ul class="nav nav-list">
        <li class="nav-header">My AeroFS</li>
        ${render_nonadmin_links()}
        <li class="nav-header">My Team</li>
        ${render_admin_links()}
        ${render_help_links()}
    </ul>
</%def>

<%def name="render_nonadmin_links()">
    <%
        links = [
            ('accept', _("Invitations")),
            # ('devices', _("Devices"))
        ]
    %>
    % for link in links:
        ${render_navigation_link(link)}
    % endfor
</%def>

<%def name="render_admin_links()">
    <%
        links = [
            ('admin_users', _("Members")),
            ('admin_shared_folders', _("Shared Folders")),
            ('admin_settings', _("Settings"))
        ]
    %>
    % for link in links:
        ${render_navigation_link(link)}
    % endfor
</%def>

<%def name="render_help_links()">
    <li class="divider"></li>
    <li><a href="http://support.aerofs.com" target="_blank">Support</a></li>
</%def>

## param link: tuple (route_name, text_to_display)
<%def name="render_navigation_link(link)">
    <li
        %if request.matched_route and request.matched_route.name == link[0]:
            class="active"
        %endif
    ><a href="${request.route_path(link[0])}">${link[1]}</a></li>
</%def>

## Remove the footer for now.
##
## sets a global variable with the current year, for copyright notices
##<%!
##    import datetime
##    year = datetime.datetime.now().year
##%>
##
##<footer>
##    <div class="container">
##        <div class="row">
##            <div class="span12">
##                <p>&copy; Air Computing Inc. ${year}</p>
##            </div>
##        </div>
##    </div>
##</footer>

## javascript
##==================================================
##Placed at the end of the document so the pages load faster

<script src="https://ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js"></script>
<script src="${request.static_url('aerofs_web.layout:static/js/jquery.easing.1.3.js')}"></script>
<script src="${request.static_url('aerofs_web.layout:static/js/message_bar.js')}"></script>
<script src="${request.static_url('aerofs_web.layout:static/js/bootstrap.js')}"></script>

<%block name="scripts"/>

</body>
</html>