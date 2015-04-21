<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Shared Folders" %>

<%namespace name="spinner" file="spinner.mako"/>

<%block name="css">
    <link rel="stylesheet" href="${request.static_path('web:static/css/compiled/my-table.min.css')}"/>
    <link rel="stylesheet" href="${request.static_path('web:static/shadowfax/css/shadowfax.css')}">
</%block>

<div xmlns:ng="http://angularjs.org" id="ngApp" ng-app="shadowfaxApp">
    <div ng-controller="SharedFoldersController">
        <div class="row">
            <div class="col-sm-12">
                <h2>${page_heading}</h2>
                <div class="my-table">
                    <div class="my-table-head row">
                        <div class="col-sm-3 hidden-xs">Folder</div>
                        <!-- We hide the owner and viewer columns in IE 8 and earlier
                        because the member lists don't render in those browsers. -->
                        <div class="col-sm-3 hidden-xs" ng-hide="isOldIE">Owners</div>
                        <div class="col-sm-4 hidden-xs" ng-hide="isOldIE">Viewers and Editors</div>
                    </div>
                    <div class="my-table-body">
                        <div ng-hide="folders.length > 0" ng-cloak class="my-row row">
                            <div class="col-xs-12" ng-cloak>No shared folders.</div>
                        </div>
                        <div ng-repeat="folder in folders" class="my-row row">
                            <div class="col-sm-3 col-xs-12" ng-cloak><strong class="visible-xs">{{folder.name}}</strong><span class="hidden-xs">{{folder.name}}</span></div>
                            <div class="col-sm-3 col-xs-4" ng-cloak><strong class="visible-xs">Owners: </strong><span ng-bind="folder.people | byRole:'Owner' | myMembers"></span></div>
                            <div class="col-sm-4 col-xs-4" ng-cloak><strong class="visible-xs">Members: </strong><span ng-bind="folder.people | byRole:'Member' | myMembers"></span></div>

                            <div class="col-sm-2 col-xs-4" id="folder-{{folder.spinnerID}}">
                                <span class="folder-spinner pull-left">&nbsp;</span>
                                <div class="sf-actions btn-group pull-right actions">
                                  <button type="button" class="btn btn-plain dropdown-toggle" data-toggle="dropdown">
                                    Actions <span class="caret"></span>
                                  </button>
                                  <ul class="dropdown-menu" role="menu">
                                    <li>
                                        <a ng-click="manage(folder)">
                                        <span class="glyphicon glyphicon-user"></span> 
                                        <span ng-if="folder.is_privileged">Manage Members</span>
                                        <span ng-if="!folder.is_privileged">View Members</span>
                                      </a>
                                    </li>
                                    <li ng-if="folder.is_member">
                                      <a ng-click="leave(folder)">
                                        <span class="icon">
                                          <img src="${request.static_path('web:static/img/icons/exit.svg')}" onerror="this.onerror=null; this.src='${request.static_path('web:static/img/icons/exit.png')}'"/>
                                        </span>
                                          Leave
                                      </a>
                                    </li>
                                    <li ng-if="folder.is_privileged">
                                      <a ng-click="destroyCheck(folder)">
                                        <span class="glyphicon glyphicon-trash"></span> 
                                          Delete
                                      </a>
                                    </li>
                                  </ul>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <br>
        <div aero-pagination
            total="paginationInfo.total"
            offset="paginationInfo.offset"
            pagelimit="paginationInfo.limit"
            callback="paginationInfo.callback(offset)"
            ng-if="paginationInfo.active"></div>
        <div class="row" ng-show="leftFolders.length > 0" ng-cloak>
            <div class="col-sm-12">
                <h2>Left folders</h2>
                <div class="my-table">
                    <div class="my-table-head row">
                    </div>
                    <div class="my-table-body">
                        <div ng-repeat="folder in leftFolders" class="my-row row">
                            <div class="col-xs-9">
                                {{folder.name}}
                            </div>
                            <div class="col-xs-3" id="left-folder-{{folder.spinnerID}}">
                                <span class="folder-spinner pull-left">&nbsp;</span>
                                <a class="btn btn-default pull-right" ng-click="rejoin(folder)">Rejoin</a>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<%block name="head_scripts">
    <script src="${request.static_path('web:static/js/angular-lib/angular/angular.min.js')}"></script>
</%block>

<%block name="scripts">
    <%spinner:scripts/>
    <script src="${request.static_path('web:static/js/angular-lib/angular-ui/ui-bootstrap-tpls-0.11.0.min.js')}"></script>
    <script src="${request.static_path('web:static/js/angular-lib/angular-strap/angular-strap.min.js')}"></script>
    <script src="${request.static_path('web:static/js/angular-lib/angular-strap/angular-strap.tpl.min.js')}"></script>
    <script type="text/javascript">
        canAdminister = "${can_administer}" == "True" ? true : false;
        hasPagination = "${has_pagination}" == "True" ? true: false;
        dataUrl = "${data_url}";
        setPermUrl = "${request.route_path('json.set_shared_folder_perm')}";
        addMemberUrl = "${request.route_path('json.add_shared_folder_perm')}";
        removeMemberUrl = "${request.route_path('json.delete_shared_folder_perm')}";
        leaveFolderUrl = "${request.route_path('json.leave_shared_folder')}";
        destroyFolderUrl = "${request.route_path('json.destroy_shared_folder')}";
        rejoinFolderUrl = "${request.route_path('json.accept_folder_invitation')}";
        paginationLimit = parseInt("${pagination_limit}", 10);
        getUsersAndGroupsURL = "${request.route_path('json.list_org_users_and_groups')}";
        getGroupsURL = "${request.route_path('json.list_org_groups')}";
        getGroupMembersURL = "${request.route_path('json.list_group_members')}";
    </script>
    <script src="${request.static_path('web:static/shadowfax/app.js')}"></script>
    <script src="${request.static_path('web:static/shadowfax/filters.js')}"></script>
    <script src="${request.static_path('web:static/ng-modules/pagination/pagination.js')}"></script>
    <script src="${request.static_path('web:static/ng-modules/typeahead/typeahead.js')}"></script>
    <script src="${request.static_path('web:static/shadowfax/controllers.js')}"></script>
</%block>
