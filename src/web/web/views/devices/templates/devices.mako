<%inherit file="dashboard_layout.mako"/>
<%! page_title = "My Devices" %>

<%!
    from web.util import is_mobile_disabled
%>

<%block name="css">
    <link rel="stylesheet" href="${request.static_path('web:static/css/compiled/my-table.min.css')}"/>
</%block>

<div ng-app="sarumanApp">
    <div class="row">
        <div class="col-sm-12">
            <h2>${page_heading}</h2>
            <div class="my-table" ng-controller="DevicesController">
                <div class="my-table-head row">
                    <div class="col-sm-6 hidden-xs">Name</div>
                    <div class="col-sm-2 hidden-xs">Most recent activity</div>
                    <div class="col-sm-2 hidden-xs">IP address</div>
                </div>
                <div class="my-table-body">
                    <div ng-repeat="device in devices" class="my-row row">
                        <div ng-device-row></div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<div class="text-muted">
    <p>
    %if are_team_servers:
       <a href="${request.route_path('download_team_server')}">Install the Team Server app</a>
    %elif is_mobile_disabled(request.registry.settings):
        Add new devices by installing <a href="${request.route_path('download')}">desktop apps</a>.
    %else:
        Add new devices by installing <a href="${request.route_path('download')}">desktop</a> or
        <a href="${request.route_path('add_mobile_device')}">mobile</a> apps.
    %endif
    </p>
</div>

<%block name="scripts">
    <script type="text/javascript">
        deviceRenameURL = "${request.route_path('json.rename_device')}";
        unlinkURL = "${request.route_path('json.unlink_device')}";  
        eraseURL = "${request.route_path('json.erase_device')}";
        revokeTokenURL = "${request.route_path('json_delete_access_token')}";
        currentUserEmail = "${user}";
    </script>
    <script src="${request.static_path('web:static/js/angular-lib/angular/angular.min.js')}"></script>
    <script src="${request.static_path('web:static/js/angular-lib/angular-ui/ui-bootstrap-tpls-0.11.0.min.js')}"></script>
    <script src="${request.static_path('web:static/saruman/app.js')}"></script>
    <script src="${request.static_path('web:static/saruman/controllers.js')}"></script>
    <script src="${request.static_path('web:static/saruman/filters.js')}"></script>
    <script src="${request.static_path('web:static/saruman/directives.js')}"></script>
</%block>
