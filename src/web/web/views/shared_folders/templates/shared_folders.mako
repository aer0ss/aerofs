<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Shared Folders" %>

<%namespace name="spinner" file="spinner.mako"/>

<%block name="css">
    <link rel="stylesheet" href="${request.static_path('web:static/css/compiled/my-table.min.css')}"/>
    <link rel="stylesheet" href="${request.static_path('web:static/shadowfax/css/shadowfax.css')}">
</%block>

<div ng-app="shadowfaxApp">
    <div ng-controller="SharedFoldersController">
        <div class="row">
            <div class="col-sm-12">
                <h2>${page_heading}</h2>
                <div class="my-table">
                    <div class="my-table-head row">
                        <div class="col-sm-3 hidden-xs">Folder</div>
                        <div class="col-sm-3 hidden-xs">Owners</div>
                        <div class="col-sm-4 hidden-xs">Viewers and Editors</div>
                    </div>
                    <div class="my-table-body">
                        <div ng-repeat="folder in folders" class="my-row row">
                            <div class="col-sm-3 col-xs-12"><strong class="visible-xs">{{folder.name}}</strong><span class="hidden-xs">{{folder.name}}</span></div>
                            <div class="col-sm-3 col-xs-4"><strong class="visible-xs">Owners: </strong>{{folder.people|byRole:"Owner"|myMembers}}</div>
                            <div class="col-sm-4 col-xs-4"><strong class="visible-xs">Members: </strong>{{folder.people|byRole:"Member"|myMembers}}</div>

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
        <div class="pagination pull-right" ng-hide="pages.length < 2">
            <a ng-hide="getCurrentPage() === 1" ng-click="showPage(getCurrentPage()-1)">&laquo;</a>
            <span ng-show="getCurrentPage() === 1">&laquo;</span>
            <span ng-repeat="number in pages">
                <a ng-click="showPage(number)" ng-hide="getCurrentPage() === number">{{number}}</a>
                <span ng-show="getCurrentPage() === number">{{number}}</span>
            </span>
            <a ng-hide="getCurrentPage() === pages.length" ng-click="showPage(getCurrentPage()+1)">&raquo;</a>
            <span ng-show="getCurrentPage() === pages.length">&raquo;</span>
        </div>
        <br><br>
        <div class="row" ng-show="leftFolders.length > 0">
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

<%block name="scripts">
    <%spinner:scripts/>
    <script src="${request.static_path('web:static/js/angular-lib/angular/angular.min.js')}"></script>
    <script src="${request.static_path('web:static/js/angular-lib/angular-ui/ui-bootstrap-tpls-0.11.0.min.js')}"></script>
    <script type="text/javascript">
        dataUrl = "${data_url}";
        setPermUrl = "${request.route_path('json.set_shared_folder_perm')}";
        addMemberUrl = "${request.route_path('json.add_shared_folder_perm')}";
        removeMemberUrl = "${request.route_path('json.delete_shared_folder_perm')}";
        leaveFolderUrl = "${request.route_path('json.leave_shared_folder')}";
        destroyFolderUrl = "${request.route_path('json.destroy_shared_folder')}";
        rejoinFolderUrl = "${request.route_path('json.accept_folder_invitation')}";
        paginationLimit = parseInt("${pagination_limit}", 10);
    </script>
    <script src="${request.static_path('web:static/shadowfax/app.js')}"></script>
    <script src="${request.static_path('web:static/shadowfax/filters.js')}"></script>
    <script src="${request.static_path('web:static/shadowfax/controllers.js')}"></script>
</%block>
