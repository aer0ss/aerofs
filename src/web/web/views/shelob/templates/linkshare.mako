<%inherit file="base_layout.mako"/>

<%!
    from web.auth import is_admin
    from web.util import is_private_deployment
%>

<%! page_title = "Download Shared Files" %>

<%def name="home_url()">
    %if not is_private_deployment(request.registry.settings):
        ${request.route_path('marketing_home')}
    %endif
</%def>

<%block name="top_navigation_bar_desktop">
    <li class="pull-right"><a href="${request.route_path('dashboard_home')}" class="btn">
        %if is_admin(request):
            Your dashboard &raquo;
        %else:
            Log into AeroFS &raquo;
        %endif
    </a></li>
</%block>

<div class="row">
    <div class="col-lg-8 col-sm-10 col-lg-offset-2 col-sm-offset-1">
        <div ng-app="shelobApp" id="shelobApp">
            <div id="linkshare">
                <div ng-view></div>
            </div>
        </div>
    </div>
</div>

<%block name="footer">
    <footer>
        <div class="container">
            <div class="row">
                <div class="col-sm-12" id="footer-span">
                    <ul class="list-inline">
                        <li class="pull-right"><a href="//aerofs.com">About AeroFS</a></li>
                    </ul>
                </div>
            </div>
        </div>
    </footer>
</%block>

<%block name="scripts">
    <script src="${request.static_path('web:static/shelob/lib/angular/angular.min.js')}"></script>
    <script src="${request.static_path('web:static/shelob/lib/angular/angular-route.min.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/app.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/filters.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/services.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/controllers.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/directives.js')}"></script>
    <script src="${request.static_path('web:static/shelob/lib/angular-ui/ui-bootstrap-tpls-0.11.0.min.js')}"></script>
    <script src="${request.static_path('web:static/shelob/lib/angular-tree-control.js')}"></script>
    <script src="${request.static_path('web:static/shelob/lib/modernizr.js')}"></script>
    <%! from web.util import is_private_deployment %>
    %if is_private_deployment(request.registry.settings):
        <script src="${request.static_path('web:static/shelob/js/config/config_private.js')}"></script>
    %else:
        <script src="${request.static_path('web:static/shelob/js/config/config_hybrid.js')}"></script>
    %endif
</%block>

<%block name="css">
    <link rel="stylesheet" href="${request.static_path('web:static/shelob/css/shelob.css')}"/>
    <link rel="stylesheet" href="${request.static_path('web:static/shelob/css/tree-control.css')}"/>
    <link rel="stylesheet" href="${request.static_path('web:static/shelob/css/tree-control-attribute.css')}"/>
</%block>