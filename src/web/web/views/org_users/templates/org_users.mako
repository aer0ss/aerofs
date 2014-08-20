<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Users" %>

<%!
    from web.util import is_private_deployment
%>

<%namespace name="modal" file="modal.mako"/>

<%block name="css">
    <link rel="stylesheet" href="${request.static_path('web:static/css/compiled/my-table.min.css')}"/>
</%block>

<div ng-app="striderApp">
    <div class="row">
        <div class="col-sm-12">
            <div ng-controller="UsersController">
                <h2>Users in my organization</h2>
                <div class="my-table">
                    <div class="my-table-head row">
                        <div class="col-sm-4 hidden-xs">Name</div>
                        <div class="col-sm-1 hidden-xs"></div>
                        <div class="col-sm-4 hidden-xs">Email</div>
                        <div class="col-sm-1 hidden-xs">2FA</div>
                        <div class="col-sm-2 hidden-xs"></div>
                    </div>
                    <div class="my-table-body">
                        <div ng-repeat="user in users" class="my-row row">
                            <div aero-user-row></div>
                        </div>
                    </div>
                </div>
                <div ng-show="pages && pages.length > 1">
                    <div aero-pagination></div>
                    <br>
                    <br>
                </div>
            </div>
            <br>

            <h3>Invite people to organization</h3>
            <br>
            <div ng-controller="InviteesController">
                <form class="form-inline" id="invite_form" method="post" ng-submit="invite()">
                    <input type="text" class="form-control" id="invite_user_email" placeHolder="Email address" ng-model="newInvitee"/>
                    <input id='invite_button' class="btn btn-primary" type="submit" value="Send Invite"/>
                </form>
                <br>
                <div class="my-table" ng-show="invitees.length > 0">
                    <div class="my-table-head row">
                        <div class="col-sm-8 hidden-xs">Invitees</div>
                    </div>
                    <div class="my-table-body">
                        <div ng-repeat="invitee in invitees" class="my-row row">
                            <div aero-invitee-row></div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<%block name="scripts">
    <script type="text/javascript">
        %if is_private_deployment(request.registry.settings):
            isPrivate = true;
        %else:
            isPrivate = false;
        %endif

        adminLevel = ${admin_level};
        userLevel = ${user_level};
        paginationLimit = parseInt("${pagination_limit}", 10);

        userFoldersURL = "${request.route_path('user_shared_folders')}";
        userDevicesURL = "${request.route_path('user_devices')}";
        userDataURL = "${request.route_path('json.list_org_users')}";
        inviteeDataURL = "${request.route_path('json.list_org_invitees')}";

        setAuthURL = "${request.route_path('json.set_auth_level')}";
        setPubURL = "${request.route_path('json.set_publisher_status')}";
        disable2faURL = "${request.route_path('json.disable_two_factor')}";
        removeUserURL = "${request.route_path('json.remove_user')}";
        deleteUserURL = "${request.route_path('json.deactivate_user')}";

        inviteURL = "${request.route_path('json.invite_user')}";
        uninviteURL = "${request.route_path('json.delete_org_invitation')}";
    </script>

    <script src="${request.static_path('web:static/js/angular-lib/angular/angular.min.js')}"></script>
    <script src="${request.static_path('web:static/js/angular-lib/angular-ui/ui-bootstrap-tpls-0.11.0.min.js')}"></script>
    <script src="${request.static_path('web:static/strider/app.js')}"></script>
    <script src="${request.static_path('web:static/shadowfax/pagination.js')}"></script>
    <script src="${request.static_path('web:static/strider/controllers.js')}"></script>
    <script src="${request.static_path('web:static/strider/directives.js')}"></script>
    <script src="${request.static_path('web:static/shadowfax/filters.js')}"></script>
</%block>
