var fellowshipControllers = angular.module('fellowshipControllers', []);

fellowshipControllers.controller('GroupsController', ['$scope', '$rootScope', '$log', '$modal', '$http',
    function($scope, $rootScope, $log, $modal, $http){
        var csrftoken = $('meta[name=csrf-token]').attr('content');
        $http.defaults.headers.common["X-CSRF-Token"] = csrftoken;

        $rootScope.knownGroupNames = [];
        var getGroupData = function () {
            var fakeData = [{
                name: 'Testers',
                members: [{email:'me@blah.com'}, {email:'another@blah.com'}]
            },
                {
                name: 'Everyone',
                members: [{email:'me@blah.com'}, {email:'another@blah.com'}, {email:'something@blah.com'},
                    {email:'forsythia@blah.com'}, {email:'agatha@blah.com'}, {email:'farallon@blah.com'},
                    {email:'ghost@blah.com'}, {email:'swartz@blah.com'}, {email:'hp@blah.com'},
                    {email:'sprout@blah.com'}, {email:'nora@blah.com'}, {email:'janie@blah.com'}]
            },
                {
                name: 'Sales',
                members: [{email:'forsythia@blah.com'}, {email:'agatha@blah.com'}, {email:'farallon@blah.com'},
                    {email:'hp@blah.com'},{email:'sprout@blah.com'}, {email:'janie@blah.com'}]
            }];
            $scope.groups = fakeData;
            $scope.total = fakeData.length;
            for (var i = 0; i < fakeData.length; i++) {
                $rootScope.knownGroupNames.push(fakeData[i].name);
            }
            $scope.calculatePages($scope.groups.length);
        };
        $scope = pagination.activate($scope, paginationLimit, getGroupData);
        getGroupData();

        $scope.getNewGroup = function () {
            return {
                name: '',
                rawMembers: '',
                members: []
            };
        };

        /*
         *
         * Email address/group member conversion functions
         * TODO: spin out into separate file, make shadowfax use these too
         */

        var _members_string_to_email_list = function(s) {
            // convert comma or whitespace delimited string to list of emails
            return $.trim(s).split(/\s*[,;\s]\s*/).filter(
                function(item){
                    return item.indexOf('@') != -1;
                });
        };

        var _email_list_to_user_objects = function(email_list) {
            // convert list of emails into list of user objects
            var userObjects = [];
            for (var i = 0; i < email_list.length; i++) {
                userObjects.push({
                    email: email_list[i]
                });
            }
            return userObjects;
        };

        var _user_objects_to_members_string = function (users) {
            // converts list of user objects to a linebreak-delimited string of emails
            s = '';
            for (var i = 0; i < users.length; i++) {
                s = s + users[i].email + '\n';
            }
            return s;
        };

        $scope.add = function() {
            var addGroupModalCtrl = function ($scope, $modalInstance, groups, newGroup) {
                $scope.groups = groups;
                $scope.newGroup = newGroup;

                $scope.ok = function () {
                    // find whitespace or comma-delimited email addresses and make an array of them
                    $scope.newGroup.members = _members_string_to_email_list($scope.newGroup.rawMembers);
                    // TODO: invite members that aren't already users
                    $http.post(addGroupURL, {
                        name: $scope.newGroup.name,
                        members: $scope.newGroup.members
                    }).success(function(response){
                        $log.info('New group "' + $scope.newGroup.name + '" created.');
                        $scope.newGroup.members = _email_list_to_user_objects($scope.newGroup.members);
                        $scope.groups.push($scope.newGroup);
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
                templateUrl: '/static/fellowship/partials/add-group.html',
                controller: addGroupModalCtrl,
                resolve: {
                    groups: function () {
                        return $scope.groups;
                    },
                    newGroup: function () {
                        return $scope.getNewGroup();
                    }
                }
            });
        };
        $scope.edit = function(group) {
            var editGroupModalCtrl = function($scope, $modalInstance, groups, group) {
                $scope.groups = groups;
                $scope.group = group;

                $scope.newGroup = group;
                $scope.newGroup.rawMembers = _user_objects_to_members_string($scope.newGroup.members);

                $scope.ok = function () {
                    for (var i = 0; i < $scope.groups.length; i++) {
                        if ($scope.groups[i].name == $scope.group.name) {
                            $scope.groups[i].name = $scope.newGroup.name;
                            $scope.groups[i].members = _email_list_to_user_objects(_members_string_to_email_list($scope.newGroup.rawMembers));
                            break;
                        }
                    }
                    $modalInstance.close();
                };
                $scope.cancel = function () {
                    $modalInstance.dismiss('cancel');
                };
            };

            var modalInstance = $modal.open({
                templateUrl: '/static/fellowship/partials/add-group.html',
                controller: editGroupModalCtrl,
                resolve: {
                    groups: function () {
                        return $scope.groups;
                    },
                    group: function () {
                        return group;
                    }
                }
            });
        };
        $scope.delete = function(group) {
            var deleteGroupModalCtrl = function($scope, $modalInstance, groups, group) {
                $scope.groups = groups;
                $scope.group = group;

                $scope.ok = function () {
                    for (var i = 0; i < $scope.groups.length; i++) {
                        if ($scope.groups[i].name == $scope.group.name) {
                            $scope.groups.splice(i,1);
                        }
                    }
                    $modalInstance.close();
                };
                $scope.cancel = function () {
                    $modalInstance.dismiss('cancel');
                };
            };

            var modalInstance = $modal.open({
                templateUrl: '/static/fellowship/partials/delete-group.html',
                controller: deleteGroupModalCtrl,
                resolve: {
                    groups: function () {
                        return $scope.groups;
                    },
                    group: function () {
                        return group;
                    }
                }
            });
        };
    }]);