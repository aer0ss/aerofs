var fellowshipControllers = angular.module('fellowshipControllers', []);

fellowshipControllers.controller('GroupsController', ['$scope', '$rootScope', '$log', '$modal', '$http',
    function($scope, $rootScope, $log, $modal, $http){
        var csrftoken = $('meta[name=csrf-token]').attr('content');
        $http.defaults.headers.common["X-CSRF-Token"] = csrftoken;
        $scope.isAdmin = isAdmin;
        $rootScope.knownGroupNames = ['Taken', 'Acme'];

        var getGroupData = function () {
            $http.get(getGroupsURL).success(function(response){
                $scope.groups = response.groups;
                $scope.paginationInfo.total = response.groups.length;
            }).error(function(response){
                showErrorMessageFromResponse(response);
            });
        };
        $scope.paginationInfo = {
            total: 0,
            offset: 0,
            limit: parseInt(paginationLimit, 10),
            callback: function(offset){
                $scope.paginationInfo.offset = offset;
                getGroupData();
            }
        };
        getGroupData();

        $scope.getNewGroup = function () {
            return {
                name: '',
                newMemberEmail: '',
                members: []
            };
        };

        var _user_objects_to_email_list = function(users) {
            return users.map(function(item){
                return item.email;
            });
        };

        var setGroupCtrlMethods = function(scope, $modalInstance) {
            scope.userDataURL = userDataURL;
            scope.nonUniqueName = false;

            scope.maxMembers = maxMembers;

            scope.addMember = function(){
                // if the member wasn't added via typeahead
                // need to populate email by name attribute
                if (scope.newGroup.newMember.email === undefined) {
                    scope.newGroup.newMember.email = scope.newGroup.newMember.name;
                }
                scope.newGroup.members.push(scope.newGroup.newMember);
                scope.newGroup.newMember = '';
            };

            scope.removeMember = function(member){
                for (var i = 0; i < scope.newGroup.members.length; i++) {
                    if (scope.newGroup.members[i].email === member.email) {
                        scope.newGroup.members.splice(i,1);
                        return;
                    }
                }
            };

            scope.cancel = function () {
                $modalInstance.dismiss('cancel');
            };

            scope.addGroup = function(group) {
                $scope.groups.push(group);
            };

            return scope;
        };

        $scope.add = function() {
            var addGroupModalCtrl = function ($scope, $modalInstance, newGroup) {
                $scope.newGroup = newGroup;

                $scope = setGroupCtrlMethods($scope, $modalInstance);

                $scope.ok = function () {
                    $http.post(addGroupURL, {
                        name: $scope.newGroup.name,
                        members: _user_objects_to_email_list($scope.newGroup.members)
                    }).success(function(response){
                        $log.info('Created new group "' + $scope.newGroup.name + '".');
                        $scope.newGroup.id = response.id;
                        $scope.addGroup($scope.newGroup);
                        $modalInstance.close();
                    }).error(function(response, status){
                        $log.warn("Group creation failed with status " + status +
                            " and type " + response.type);
                        if (response.type == "NOT_FOUND") {
                            $scope.newGroup.members = [];
                            $scope.addGroup($scope.newGroup);
                        }
                        $modalInstance.close();
                        showErrorMessage(response.message);
                    });
                };
            };

            var modalInstance = $modal.open({
                templateUrl: '/static/fellowship/partials/add-group.html',
                controller: addGroupModalCtrl,
                resolve: {
                    newGroup: function () {
                        return $scope.getNewGroup();
                    }
                }
            });
        };

        $scope.syncNow = function() {
            $log.info('admin started LDAP group syncing');
            // if the request takes more than 500 milliseconds, give user immediate feedback
            var nowSyncing=setTimeout(function() {
                showSuccessMessage("Now syncing groups with LDAP");
            }, 500);
            $http.post(syncGroupsURL)
            .success(function(response) {
                clearTimeout(nowSyncing);
                $log.info('LDAP group syncing completed');
                showSuccessMessage("Successfully synced groups with LDAP");
            }).error(function(response, status) {
                clearTimeout(nowSyncing);
                $log.warn("failed ldap group syncing, error response with status " + status +
                    " and type " + response.type);
                showErrorMessageUnsafe("We encountered an error while syncing with LDAP, " +
                    "if the problem persists please contact " +
                    "<a href='mailto:support@aerofs.com' target='_blank'>" +
                    "support@aerofs.com</a>.");
            });
        }

        $scope.edit = function(group) {
            var editGroupModalCtrl = function($scope, $modalInstance, group) {
                $scope.group = group;
                $scope.newGroup = angular.copy(group);

                $scope = setGroupCtrlMethods($scope, $modalInstance);

                $scope.ok = function () {
                    $http.post(editGroupURL, {
                        'id': $scope.group.id,
                        'name': $scope.newGroup.name,
                        'members': _user_objects_to_email_list($scope.newGroup.members)
                    }).success(function(response){
                        $log.info('Updated group "' + $scope.group.name + '".');
                        $scope.group.name = $scope.newGroup.name;
                        $scope.group.members = $scope.newGroup.members;
                        $modalInstance.close();
                    }).error(function(response, status){
                        $log.warn("Group update failed with status " + status +
                            " and type " + response.type);
                        if (response.type == "NOT_FOUND") {
                            // safe to update group name
                            // failure was in the members
                            $scope.group.name = $scope.newGroup.name;
                        }
                        $modalInstance.close();
                        showErrorMessage(response.message);
                    });
                };
            };

            var modalInstance = $modal.open({
                templateUrl: '/static/fellowship/partials/add-group.html',
                controller: editGroupModalCtrl,
                resolve: {
                    group: function () {
                        return group;
                    }
                }
            });
        };

        var removeGroup = function(group) {
            for (var i = 0; i < $scope.groups.length; i++) {
                if ($scope.groups[i].name == group.name) {
                    $scope.groups.splice(i,1);
                }
            }
        };
        $scope.delete = function(group) {
            var deleteGroupModalCtrl = function($scope, $modalInstance, group, removeGroup) {
                $scope.group = group;
                $scope.removeGroup = removeGroup;

                $scope.ok = function () {
                    $http.post(removeGroupURL, {
                        'id': $scope.group.id
                    }).success(function(response){
                        removeGroup($scope.group);
                        $modalInstance.close();
                    }).error(function(response){
                        showErrorMessageFromResponse(response);
                        $modalInstance.close();
                    });
                };
                $scope.cancel = function () {
                    $modalInstance.dismiss('cancel');
                };
            };

            var modalInstance = $modal.open({
                templateUrl: '/static/fellowship/partials/delete-group.html',
                controller: deleteGroupModalCtrl,
                resolve: {
                    removeGroup: function () {
                        return removeGroup;
                    },
                    group: function () {
                        return group;
                    }
                }
            });
        };
    }]);
