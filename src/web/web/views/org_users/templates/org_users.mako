<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Users" %>

<%!
    from web.auth import is_admin
%>

<%namespace name="modal" file="modal.mako"/>

<%block name="css">
    <link rel="stylesheet" href="${request.static_path('web:static/css/compiled/my-table.min.css')}"/>
    <style>
        .user-search {
          margin-bottom: 0;
        }
        .result-count {
          color: gray;
          font-size: 12px;
          margin-top: 5px;
          margin-left: -15px;
        }
        .user-count-header {
          margin-top: 0;
          margin-bottom: 10px;
        }
        .user-count-header .total {
          color: gray;
        }
        #invite_form {
          padding: 0;
        }
        #invite_user_email {
          background-color: #fff !important;
          border: none;
          border-color: transparent;
          border-bottom: 1px dotted #ddd;
          box-shadow: none;
          border-radius: 0;
          -webkit-box-shadow: none;
          -webkit-border-radius: 0;
          padding-left: 0px;
        }
    </style>
</%block>

<div xmlns:ng="http://angularjs.org" id="ngApp" ng-app="striderApp">
    <div class="row">
        <div class="col-sm-12">
            <div ng-controller="UsersController" ng-show="sharedProperties.usersView" ng-cloak>
                <div class="row">
                    <h2 class="col-sm-8">Users in my organization</h2>
                    <div class="my-search col-sm-4 pull-right">
                        <form role="form" class="form-horizontal" name="form">
                            <div class="form-group user-search">
                                <div id="org-users-search"
                                    aero-list-typeahead
                                    label=""
                                    ng-model="user"
                                    async-attr="data"
                                    async-data="{{userDataURL}}"
                                    placeholder="Search Users"
                                    title="Search users"
                                    parent-update="updateUsers(matches, total, substring)"
                                    on-clear="restore()"
                                ></div>
                            </div>
                        </form>
                        <div class="result-count" ng-show="searchResultCount > 0">
                            {{ searchResultCount }} user{{ searchResultCount != 1 ? 's': ''}} found
                        </div>
                    </div>
                </div>
                <div class="row user-count-header">
                    <div class="col-sm-3 total"> Total: {{ sharedProperties.userCount }} </div>
                    %if is_admin(request):
                    <div class="clearfix"></div>
                    <div class="col-sm-3 pending-invitees-link">
                        <a href="" ng-click="toggleUsersView()" ng-show="sharedProperties.inviteesCount > 0">
                            Pending Invites ( {{ sharedProperties.inviteesCount }} )
                        </a>
                        <a href="" ng-click="toggleUsersView()" ng-show="sharedProperties.inviteesCount == 0">
                            No Pending Invites
                        </a>
                    </div>
                    %endif
                </div>
                <div class="my-table">
                    <div class="my-table-head row">
                        <div class="col-sm-4 hidden-xs">Name</div>
                        <div class="col-sm-1 hidden-xs"></div>
                        <div class="col-sm-4 hidden-xs">Email</div>
                        <div ng-show="isAdmin" class="col-sm-1 hidden-xs">2FA</div>
                        <div class="col-sm-2 hidden-xs"></div>
                    </div>
                    <div class="my-table-body">
                        <div ng-repeat="user in users" class="my-row row">
                            <div aero-user-row></div>
                        </div>
                    </div>
                </div>
                <div aero-pagination
                    total="paginationInfo.total"
                    offset="paginationInfo.offset"
                    pagelimit="paginationInfo.limit"
                    callback="paginationInfo.callback(offset, substring)">
                </div>
            </div>

        %if is_admin(request):
            <div ng-controller="InviteesController" ng-hide="sharedProperties.usersView" ng-cloak>
                <div class="row">
                    <h2 class="col-sm-6">Pending Invites</h2>
                </div>
                <div class="row user-count-header">
                    <div class="col-sm-3 total"> Total: {{ sharedProperties.inviteesCount }} </div>
                    <div class="clearfix"></div>
                    <div class="col-sm-3 users-link">
                        <a href="" ng-click="toggleUsersView()">
                            Users ( {{ sharedProperties.userCount }} )
                        </a>
                    </div>
                </div>
                <div class="my-table">
                    <div class="my-table-head row">
                        <div class="col-sm-8 hidden-xs">Email</div>
                    </div>
                    <div class="my-table-body">
                        <div class="my-row row">
                            <div class="col-sm-12">
                                <div class="row org-user" style="padding-bottom: 10px;">
                                    <form class="form" id="invite_form" method="post" ng-submit="invite()">
                                        <div class="col-sm-8">
                                            <input id="invite_user_email" class="form-control" type="text" placeHolder="Enter email address" ng-model="newInvitee"/>
                                        </div>
                                        <div class="col-sm-4 pull-right">
                                            <input id='invite_button' class="btn btn-primary pull-right" type="submit" value="Send invite"/>
                                        </div>
                                    </form>
                                </div>
                            </div>
                        </div>
                        <div ng-repeat="invitee in invitees" class="my-row row">
                            <div aero-invitee-row></div>
                        </div>
                    </div>
                </div>
            </div>
        %endif
        </div>
    </div>
</div>

<%block name="scripts">

    <script type="text/javascript">
        %if is_admin(request):
            isAdmin = true;
        %else:
            isAdmin = false;
        %endif
        adminLevel = ${admin_level};
        userLevel = ${user_level};
        paginationLimit = parseInt("${pagination_limit}");

        userFoldersURL = "${request.route_path('user_shared_folders')}";
        userDevicesURL = "${request.route_path('user_devices')}";
        userDataURL = "${request.route_path('json.list_org_users')}";
        inviteeDataURL = "${request.route_path('json.list_org_invitees')}";

        setAuthURL = "${request.route_path('json.set_auth_level')}";
        setPubURL = "${request.route_path('json.set_publisher_status')}";
        disable2faURL = "${request.route_path('json.disable_two_factor')}";
        deleteUserURL = "${request.route_path('json.deactivate_user')}";

        inviteURL = "${request.route_path('json.invite_user')}";
        uninviteURL = "${request.route_path('json.delete_org_invitation')}";
    </script>
    <script src="${request.static_path('web:static/js/spin.min.js')}"></script>
    <script src="${request.static_path('web:static/js/compiled/spinner.js')}"></script>
    <script src="${request.static_path('web:static/js/angular-lib/angular/angular.min.js')}"></script>
    <script src="//ajax.googleapis.com/ajax/libs/angularjs/1.2.11/angular-sanitize.js"></script>
    <script src="${request.static_path('web:static/js/angular-lib/angular-ui/ui-bootstrap-tpls-0.11.0.min.js')}"></script>
    <script src="${request.static_path('web:static/strider.js')}"></script>
    <script src="${request.static_path('web:static/shadowfax.js')}"></script>
    <script src="${request.static_path('web:static/ng-modules/pagination/pagination.js')}"></script>
    <script src="${request.static_path('web:static/ng-modules/typeahead/typeahead.js')}"></script>
</%block>
