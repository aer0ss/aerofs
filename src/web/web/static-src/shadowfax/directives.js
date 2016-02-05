var shadowfaxDirectives = angular.module('shadowfaxDirectives', ['typeahead']);

//This service is used to manage and share a folder
shadowfaxDirectives.directive('aeroSharedFolderManager',['$modal','$log', '$q', '$http', '$timeout', 'API', function($modal, $log, $q, $http, $timeout, API) {

    return {
        restrict: 'E',
        replace: true,
        scope: {
            folder: '=',
            me: '='
        },
        templateUrl:'/static/shadowfax/partials/share-folder.html',
        link: function (scope) {

            scope.openModal = function () {

                var ManageExternalModalCtrl = function ($scope, $modalInstance) {
                    $scope.external_cancel = function () {
                        $modalInstance.close();
                    };
                    $scope.external_ok = function () {
                        $modalInstance.close(true);
                    }
                };

                var ManageModalCtrl = function ($scope, $modalInstance) {
                    // See if this is being requested in IE 8 or lower
                    var isOldIE = $('html').is('.ie6, .ie7, .ie8');
                    $scope.me = scope.me;
                    $scope.folder = scope.folder;
                    $scope.error = false;
                    $scope.inviteeRole = 'Viewer';
                    $scope.canAdminister = (typeof canAdminister !== 'undefined') && canAdminister || false;

                    // Not sparta - used for typeahead
                    $scope.getUsersAndGroupsURL = getUsersAndGroupsURL;

                    $scope.getGroupMembers = function (group) {
                        API.get('/shares/' + $scope.folder.sid + '/groups/' + group.id, {}, {version: '1.3'})
                        .then(function (response) {
                            group.members = response.members || [];
                        }, function (response) {
                            $log.error(response);
                        });
                    };

                    $scope.newMember = function () {
                        return {
                            email: '',
                            is_pending: true,
                            is_owner: false,
                            can_edit: false
                        };
                    };

                    // make groups know their own members
                    // inside of member management modal
                    for (var i = 0; i < $scope.folder.people.length; i++) {
                        var entity = $scope.folder.people[i];
                        if (entity.is_group) {
                            $scope.getGroupMembers(entity);
                        }
                    }

                    /* Spinner stuff */
                    initializeSpinners();
                    function startModalSpinner() {
                        if (!isOldIE) {
                            startSpinner($('#modal-spinner'), 3);
                        }
                    }

                    function stopModalSpinner() {
                        if (!isOldIE) {
                            stopSpinner($('#modal-spinner'));
                        }
                    }

                    /* Convert string descriptor of role to
                     permissions list expected by server */
                    function getJsonPermissions(role) {
                        return ({
                            "Owner": ["MANAGE", "WRITE"],
                            "Editor": ["WRITE"],
                            "Viewer": [],
                            "Manager": ["MANAGE"]
                        })[role];
                    };

                    function setEntityPermissions(entity, role) {
                        // casting to boolean
                        entity.is_owner = role == "Owner" || false;
                        entity.can_edit = (role == "Owner" || role == "Editor") || false;
                        entity.permissions = getJsonPermissions(role);
                        return entity;
                    }

                    function showModalErrorMessage(message, response) {
                        if (response && response.status == 403) {
                            $scope.error = message +
                                " It appears you are not authorized to administer this folder. " +
                                "Please try reloading the page or logging back in.";
                        } else if (response && (response.message || response.data.message)) {
                            $scope.error = message + ' ' + (response.message || response.data.message) + '.';
                        } else {
                            $scope.error = message;
                        }
                    };

                    function externalConfirmModal() {
                        return $modal.open({
                            templateUrl: '/static/shadowfax/partials/confirm-external.html',
                            controller: ManageExternalModalCtrl
                        });
                    }

                    function handleErrorResponse(response, msg) {
                        msg = msg || "Sorry, the invitation failed."
                        stopModalSpinner();
                        showModalErrorMessage(msg, response);
                    }

                    var makeMemberRequest = function (entity, permissions) {
                        $q.when()
                        .then (function () {
                            if (!$scope.folder.sid) {
                                return API.put('/folders/' + $scope.folder.id + '/is_shared', {}, {}, {version: '1.4'})
                            }
                        }, function (response) {
                            return $q.reject(response);
                        })
                        .then(function (response) {
                            if ($scope.folder.sid || response) {
                                $scope.folder.sid = $scope.folder.sid || response.data.sid;

                                var endpoint = entity.is_group ? '/groups' : '/pending';
                                var data = { permissions: getJsonPermissions(permissions)};

                                if (entity.is_group) {
                                    data.id = entity.id;
                                } else {
                                    data.email = entity.email;
                                }
                                return API.post('/shares/' + $scope.folder.sid + endpoint, data, {}, {version: '1.3'});
                            }
                        }, function (response){
                            return $q.reject(response);
                        })
                        .then(function () {
                            var newMember = $scope.newMember();
                            newMember.is_group = entity.is_group;

                            if (newMember.is_group) {
                                newMember.id = entity.id;
                                newMember.name = entity.name;
                            } else {
                                newMember.email = entity.email;
                                newMember.first_name = entity.first_name;
                                newMember.last_name = entity.last_name;
                            }
                            $scope.folder.is_shared = true;
                            $scope.folder.is_shared = true;
                            setEntityPermissions(newMember, permissions);
                            $scope.folder.people.push(newMember);

                        }, function (response) {
                            if (response && response.status == 501) {
                                showModalErrorMessage("" +
                                    "Sorry, that action is not supported in this " +
                                    "version of AeroFS. Please upgrade your AeroFS " +
                                    "client. "
                                );
                            } else {
                                handleErrorResponse(response);
                            }
                        });
                    };

                    $scope.ok = function () {
                        $modalInstance.close();
                    };

                    $scope.inviteMembers = function (invitee, inviteeRole) {
                        if (!invitee) {
                            return;
                        }

                        $scope.error = false;
                        startModalSpinner();

                        // is_group is only set if we're receiving data from autocomplete
                        if (invitee.is_group === true) {
                            for (var i = 0; i < $scope.folder.people.length; i++) {
                                if ($scope.folder.people[i].is_group && $scope.folder.people[i].id == invitee.id) {
                                    $log.info("cannot invite group " + invitee.name + " they're already in this folder");
                                    $scope.error = "The group " + invitee.name + " is already in this folder."
                                    stopModalSpinner();
                                    return;
                                }
                            }
                            $log.info("inviting group " + invitee.id + " to folder");
                            makeMemberRequest(invitee, inviteeRole);
                        } else if (invitee.is_group === false) {
                            for (var i = 0; i < $scope.folder.people.length; i++) {
                                if ((!$scope.folder.people[i].is_group) && $scope.folder.people[i].email == invitee.email) {
                                    $log.info("cannot invite " + invitee.name + " they're already in this folder");
                                    $scope.error = invitee.name + " is already in this folder."
                                    stopModalSpinner();
                                    return;
                                }
                            }
                            $log.info("inviting user " + invitee.email + " to folder");
                            makeMemberRequest(invitee, inviteeRole);
                        } else {
                            // no data available from autocomplete, will have to parse a string of emails
                            var splitList = $.trim(invitee.name).split(/\s*[,;()\s]\s*/);
                            var emailList = splitList.filter(function (item) {
                                return item.search('@') != -1;
                            });
                            for (var i = 0; i < emailList.length; i++) {
                                $log.info("inviting user " + emailList[i] + " to folder");
                                makeMemberRequest({'email': emailList[i]}, inviteeRole);
                            }
                        }
                        stopModalSpinner();
                    };

                    //Update for pending users does not exist
                    //So we use the
                    $scope.changePerms = function (entity, role) {
                        startModalSpinner();

                        $log.info("Changing permissions for "
                            + (entity.email || entity.name)
                            + " on folder " + $scope.folder.name);

                        var memberEndpoint = '/members/' + entity.email;
                        var groupEndpoint = '/groups/' + entity.id;
                        var endpoint = entity.is_group ? groupEndpoint : memberEndpoint;

                        $q.when()
                        .then( function () {
                            if ( entity.is_pending ) {
                                return $http.post(setPermUrl,
                                    {
                                        store_id: $scope.folder.sid,
                                        is_group: entity.is_group,
                                        subject_id: entity.email || entity.id,
                                        permissions: getJsonPermissions(role),
                                        suppress_sharing_rules_warnings: true
                                    }
                                );
                            } else {
                                return API.put('/shares/' + $scope.folder.sid + endpoint,
                                    {
                                        permissions: getJsonPermissions(role)
                                    },
                                    {},
                                    {version: '1.3'}
                                );
                            }
                        })
                        .then(function () {
                            setEntityPermissions(entity, role);
                            stopModalSpinner();
                        }, function (response){
                            handleErrorResponse(response, "Failed to update permissions for "
                                + (entity.email || entity.name));
                        });
                    };

                    $scope.remove = function (entity, $index) {
                        startModalSpinner();

                        var identifier = entity.email || entity.id;
                        $log.info("Removing " + (entity.email || entity.name) + " from folder " + $scope.folder.name);

                        var memberType = entity.is_pending ? '/pending/' : '/members/';
                        var memberEndpoint = memberType + identifier;
                        var groupEndpoint = '/groups/' + identifier;
                        var endpoint = entity.is_group ? groupEndpoint : memberEndpoint;

                        API.delete('/shares/' + $scope.folder.sid + endpoint, {}, {version:'1.3'})
                        .then(function () {
                            $scope.folder.people.splice($index, 1);
                            stopModalSpinner();
                        }, function (response){
                            handleErrorResponse(response, "Failed to remove member "
                                + (entity.email || entity.name) + ".");
                        });
                    };
                };

                return $modal.open({
                    templateUrl: '/static/shadowfax/partials/manage-members.html',
                    controller: ManageModalCtrl,
                    resolve: {
                        folder: function () {
                            return scope.folder;
                        }
                    }
                });
            }
        }
    }
}]);