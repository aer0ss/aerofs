<%inherit file="dashboard_layout.mako"/>
<%! page_title = "My Files" %>

<div ng-app="shelobApp">
<div ng-view></div>
</div>

## if you modify the list of scripts, make sure to update jstest/shelob/e2e/index.html
<%block name="scripts">
    <script src="${request.static_path('web:static/shelob/lib/angular/angular.min.js')}"></script>
    <script src="${request.static_path('web:static/shelob/lib/angular/angular-route.min.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/app.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/services.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/controllers.js')}"></script>
</%block>
