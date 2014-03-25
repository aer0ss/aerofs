<%inherit file="dashboard_layout.mako"/>
<%! page_title = "My Files" %>

<div ng-app="shelobApp">
<div ng-view></div>
</div>

## if you modify the list of scripts, make sure to update jstest/shelob/e2e/index.html
<%block name="scripts">
    <script src="${request.static_path('web:static/shelob/lib/angular/angular.min.js')}"></script>
    <script src="${request.static_path('web:static/shelob/lib/angular/angular-route.min.js')}"></script>
    <script src="${request.static_path('web:static/shelob/lib/angular-ui/ui-bootstrap-tpls-0.8.0.min.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/app.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/services.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/controllers.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/directives.js')}"></script>

    <%! from web.util import is_private_deployment %>
    %if is_private_deployment(request.registry.settings):
        <script src="${request.static_path('web:static/shelob/js/config/config_private.js')}"></script>
    %else:
        <script src="${request.static_path('web:static/shelob/js/config/config_hybrid.js')}"></script>
    %endif
</%block>

<%block name="css">
    <link rel="stylesheet" href="${request.static_path('web:static/shelob/css/shelob.css')}"></script>
</%block>
