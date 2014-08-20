<%inherit file="dashboard_layout.mako"/>

<%! page_title = "Groups" %>

<%block name="css">
    <link rel="stylesheet" href="${request.static_path('web:static/css/compiled/my-table.min.css')}"/>
    <style>
        .member-list {
            columns:9em; -moz-columns:9em; -webkit-columns:9em;
        }
    </style>
</%block>

<div class="row" ng-app="fellowshipApp">
    <div class="col-sm-12">
        <h2>${organization_name} Groups</h2>

        <div ng-controller="GroupsController">
            <a class='btn btn-primary' ng-click="add()">Add new group</a>
            <br><br>
            <div class="my-table">
                <div class="my-table-head row">
                    <div class="col-sm-2 hidden-xs">Name</div>
                    <div class="col-sm-8 hidden-xs">Members</div>
                    <div class="col-sm-2 hidden-xs"></div>
                </div>
                <div class="my-table-body">
                    <div ng-repeat="group in groups" class="my-row row">
                        <div aero-group-row></div>
                    </div>
                </div>
            </div>

            <br>
            <div aero-pagination></div>
        </div>
    </div>
</div>

<%block name="scripts">
    <script type="text/javascript">
        addGroupURL = "${request.route_path('json.add_org_group')}";
        paginationLimit = 20;
    </script>
    <script src="${request.static_path('web:static/js/angular-lib/angular/angular.min.js')}"></script>
    <script src="${request.static_path('web:static/js/angular-lib/angular-ui/ui-bootstrap-tpls-0.11.0.min.js')}"></script>
    <script src="${request.static_path('web:static/fellowship/app.js')}"></script>
    <script src="${request.static_path('web:static/shadowfax/pagination.js')}"></script>
    <script src="${request.static_path('web:static/fellowship/controllers.js')}"></script>
    <script src="${request.static_path('web:static/fellowship/filters.js')}"></script>
    <script src="${request.static_path('web:static/fellowship/directives.js')}"></script>
</%block>