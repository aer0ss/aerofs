<%inherit file="dashboard_layout.mako"/>

<%! page_title = "Groups" %>

<%!
    from web.auth import is_admin
%>

<%namespace name="spinner" file="spinner.mako"/>

<%block name="css">
    <link rel="stylesheet" href="${request.static_path('web:static/css/compiled/my-table.min.css')}"/>
</%block>

<div xmlns:ng="http://angularjs.org" id="ngApp" class="row" ng-app="fellowshipApp">
    <div class="col-sm-12">
        <h2>${organization_name} Groups</h2>

        <div ng-controller="GroupsController">
            <div ng-if="isAdmin">
                <div class="row">
                    <div class="col-sm-6">
                        <a class='btn btn-primary' ng-click="add()">Add new group</a>
                    </div>
                    %if groupsyncing_enabled:
                        <div class="col-sm-6">
                            <a class='btn btn-primary' ng-click="syncNow()">Sync with LDAP</a>
                        </div>
                    %endif
                </div>
                <br><br>
            </div>

            <div class="my-table" ng-show="groups.length > 0">
                <div class="my-table-head row">
                    <div class="col-sm-2 hidden-xs">Name</div>
                    <div class="col-sm-8 hidden-xs">Members</div>
                    <div class="col-sm-2 hidden-xs"></div>
                </div>
                <div class="my-table-body">
                    <div ng-repeat="group in groups | orderBy:['name']" class="my-row row">
                        <div aero-group-row></div>
                    </div>
                </div>
            </div>

            <br>
            <div aero-pagination
                    total="paginationInfo.total"
                    offset="paginationInfo.offset"
                    pagelimit="paginationInfo.limit"
                    callback="paginationInfo.callback(offset)"></div>
            <p class="help-block">Group-sharing groups let you <strong>share folders with an entire set of people</strong> instead of having to invite each person individually.</p>
            <p class="help-block">Any organization member can invite a group to a folder that they administer. <strong>Only organization admins can create or modify the members of a group.</strong></p>
        </div>
    </div>
</div>

<%block name="scripts">
    <%spinner:scripts/>
    <script type="text/javascript">
        %if is_admin(request):
            isAdmin = true;
        %else:
            isAdmin = false;
        %endif

        userDataURL = "${request.route_path('json.list_org_users')}";
        getGroupsURL = "${request.route_path('json.list_org_groups')}";
        addGroupURL = "${request.route_path('json.add_org_group')}";
        editGroupURL = "${request.route_path('json.edit_org_group')}";
        removeGroupURL = "${request.route_path('json.remove_org_group')}";
        syncGroupsURL = "${request.route_path('json.sync_groups')}";
        paginationLimit = "${pagination_limit}";
        maxMembers = parseInt("${member_limit}");
    </script>
    <script src="${request.static_path('web:static/js/angular-lib/angular/angular.min.js')}"></script>
    <script src="${request.static_path('web:static/js/angular-lib/angular-ui/ui-bootstrap-tpls-0.11.0.min.js')}"></script>
    <script src="${request.static_path('web:static/fellowship/app.js')}"></script>
    <script src="${request.static_path('web:static/ng-modules/pagination/pagination.js')}"></script>
    <script src="${request.static_path('web:static/ng-modules/typeahead/typeahead.js')}"></script>
    <script src="${request.static_path('web:static/fellowship/controllers.js')}"></script>
    <script src="${request.static_path('web:static/fellowship/filters.js')}"></script>
    <script src="${request.static_path('web:static/fellowship/directives.js')}"></script>
</%block>
