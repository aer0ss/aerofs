var striderControllers = angular.module('striderControllers',['ngSanitize']);

striderControllers.controller('UsersController', ['$scope', '$rootScope', '$log', '$modal', '$http', 'sharedProperties',
    function($scope, $rootScope, $log, $modal, $http, sharedProperties){
        var csrftoken = $('meta[name=csrf-token]').attr('content');
        $http.defaults.headers.common["X-CSRF-Token"] = csrftoken;
        $scope.userFoldersURL = userFoldersURL;
        $scope.userDevicesURL = userDevicesURL;
        $scope.userDataURL = userDataURL;
        $scope.isAdmin = isAdmin;
        $scope.searchResultCount = 0;
        $scope.sharedProperties = sharedProperties;

        $scope.toggleUsersView = function () {
            $scope.sharedProperties.usersView = !$scope.sharedProperties.usersView
        }

        var getUsersData = function(message) {
            $log.info(message);
            var params = {
                offset: $scope.paginationInfo.offset.toString()
            }

            if ($scope.substring) {
                params.substring = $scope.substring;
            }

            $http.get(userDataURL, {
                params: params
            }).success(function(response){
                $scope.users = response.data;
                $scope.paginationInfo.total = response.total;

                $rootScope.use_restricted = response.use_restricted;
                $rootScope.me = response.me;

                setCache();
                updateUserCount();
            }).error(function(response) {
                $log.warn(response);
            });
        };

        $scope.initialLoad = {};
        $scope.paginationInfo = {
            total: 0,
            offset: 0,
            limit: parseInt(paginationLimit, 10),
            callback: function(offset, substring) {
                $scope.paginationInfo.offset = offset;
                $scope.substring = substring || '';

                getUsersData('Retrieving new page data');
            }
        };

        var setCache = function() {
            // For the search feature -- we don't want to
            // have to search when user clears entry.
            if (!$scope.initialLoad.users) {
                $scope.initialLoad.users = $scope.users;
            }

            if (!$scope.initialLoad.total) {
                $scope.initialLoad.total = $scope.paginationInfo.total;
            }
        };

        var populateFromCache = function() {
            $scope.paginationInfo.total = $scope.initialLoad.total;
            $scope.users = $scope.initialLoad.users;
        };

        var updateUserCount = function() {
            $scope.sharedProperties.userCount = $scope.paginationInfo.total;
        };

        getUsersData('Retrieving initial data');

        $scope.updateUsers = function(matches, total, substring) {
            $scope.users = matches;
            $scope.substring = substring;
            $scope.paginationInfo.total = total;
            $scope.paginationInfo.offset = 0;
            $scope.searchResultCount = total;
        };

        $scope.restore = function() {
            $scope.substring = '';
            $scope.paginationInfo.offset = 0;
            $scope.searchResultCount = 0;

            if ($scope.initialLoad.users && $scope.initialLoad.users.length){
                populateFromCache();
            } else {
                getUsersData('Retrieving new page data on clear.');
            }

            updateUserCount();
        };
        $scope.toggleAdmin = function(user) {
            user.is_admin = !user.is_admin;
            $http.post(setAuthURL, {
                'user': user.email,
                'level': user.is_admin ? adminLevel : userLevel
            }).success(function(response){
                if (user.is_admin) {
                    $log.info('User ' + user.email + ' is now an admin.');
                } else {
                    $log.info('User ' + user.email + ' is no longer an admin.');
                }
            }).error(function(data, status){
                showErrorMessageWith(data, status);
                user.is_admin = !user.is_admin;
            });
        };

        $scope.togglePublisher = function(user) {
            user.is_publisher = !user.is_publisher;
            $http.post(setPubURL, {
                'user': user.email,
                'is_publisher': user.is_publisher
            }).success(function(response){
                if (user.is_publisher) {
                    $log.info('User ' + user.email + ' is now a publisher.');
                } else {
                    $log.info('User ' + user.email + ' is no longer a publisher.');
                }
            }).error(function(data, status){
                showErrorMessageWith(data, status);
                user.is_publisher = !user.is_publisher;
            });
        };

        $scope.disable2fa = function(user) {
            var disable2faModalCtrl = function ($scope, $modalInstance, user) {
                $scope.user = user;
                $scope.cancel = function () {
                    $modalInstance.close();
                };

                $scope.ok = function () {
                    $scope.user.has_two_factor = false;
                    $.post(disable2faURL, {
                            "user": $scope.user.email
                        }
                    ).success(function(response) {
                        $log.info("Two factor authentication has been disabled for " +
                            $scope.user + ".");
                        $modalInstance.close();
                    }).error(function(data, status){
                        showErrorMessageWith(data, status);
                        $scope.user.has_two_factor = true;
                        $modalInstance.close();
                    });
                };
            };
            var modalInstance = $modal.open({
                templateUrl: '/static/strider/partials/disable-2fa-modal.html',
                controller: disable2faModalCtrl,
                resolve: {
                    user: function() {
                        return user;
                    }
                }
            });
        };

        $scope["delete"] = function(user) {
            var deleteUserModalCtrl = function ($scope, $modalInstance, user, users) {
                $scope.user = user;
                $scope.users = users;

                $scope.cancel = function () {
                    $modalInstance.close();
                };

                $scope.ok = function () {
                    $http.post(deleteUserURL, {
                            "user": $scope.user.email,
                            "erase_devices": $scope.user.eraseDevices ? true : false
                        }
                    ).success(function(response) {
                        $log.info(user.email + " has been deleted.");
                        // Remove from list of users.
                        for (var i = 0; i < $scope.users.length; i++) {
                            if ($scope.users[i].email == $scope.user.email) {
                                $scope.users.splice(i,1);
                            }
                        }
                        getUsersData('Refresh after delete');
                        $modalInstance.close();
                    }).error(function(data, status){
                        showErrorMessageWith(data, status);
                        $modalInstance.close();
                    });
                };
            };
            var modalInstance = $modal.open({
                templateUrl: '/static/strider/partials/delete-user-modal.html',
                controller: deleteUserModalCtrl,
                resolve: {
                    user: function() {
                        return user;
                    },
                    users: function() {
                        return $scope.users;
                    }
                }
            });
        };
    }]);

striderControllers.controller('InviteesController', ['$scope', '$log', '$http', '$modal', 'sharedProperties',
    function($scope, $log, $http, $modal, sharedProperties){
        var csrftoken = $('meta[name=csrf-token]').attr('content');
        $http.defaults.headers.common["X-CSRF-Token"] = csrftoken;
        $scope.newInvitee = '';
        $scope.sharedProperties = sharedProperties;
        $scope.toggleUsersView = function () {
            $scope.sharedProperties.usersView = !$scope.sharedProperties.usersView
        };

        //POPULATE INITIAL INVITEES
        $http.get(inviteeDataURL).success(function(response){
            $scope.invitees = response.invitees;
            updateInviteeCount();
        }).error(function(response){
            $log.warn(response);
        });

        var updateInviteeCount = function() {
            $scope.sharedProperties.inviteesCount = $scope.invitees.length;
        };

        //An email is passed in from the re-invite CTA.
        //newInvitee is updated through the form.
        //If a user types into the form  an email that is already in the invite
        //list, it should be handled as a re-invite.
        //Make sure the user wants to actually resend the invite

        $scope.invite = function() {
            var inviteesSoFar = $scope.invitees.map(function (i) { return i.email });

            if (inviteesSoFar.indexOf($scope.newInvitee) < 0 ) {
                $http.post(inviteURL, {
                    'user': $scope.newInvitee
                }).success(function(response){
                    $log.info('Invitation has been sent.');
                    $scope.invitees.push({email: $scope.newInvitee});
                    updateInviteeCount();
                    showSuccessMessage('Successfully invited ' + $scope.newInvitee + '.');
                    $scope.newInvitee = '';
                }).error(showErrorMessageWith);
            } else {
                $scope.reinvite($scope.newInvitee, function () {
                    $scope.newInvitee = '';
                });
            }
        };

        $scope.uninvite = function(invitee) {
            $http.post(uninviteURL, {
                'user': invitee.email
            }).success(function(response){
                $log.info('Invitation has been revoked.');
                for (var i = 0; i < $scope.invitees.length; i++) {
                    if ($scope.invitees[i].email == invitee.email) {
                        $scope.invitees.splice(i,1);
                        break;
                    }
                }
                updateInviteeCount();
                showSuccessMessage('Invitation to ' + invitee.email + ' has been revoked.');
            }).error(showErrorMessageWith);
        };

        $scope.reinvite = function(invitee, cb) {
            var reinviteModalCtrl = function ($scope, $modalInstance, invitee) {
                $scope.invitee = invitee;
                $scope.cancel = function () {
                    $modalInstance.close();
                };

                $scope.resend = function () {
                   $http.post(inviteURL, {
                       'user': $scope.invitee
                   }).success(function(response) {
                       showSuccessMessage('Successfully re-invited ' + $scope.invitee + '.');
                       $modalInstance.close();
                       if (cb) { cb(); }
                   }).error(function(data,status){
                       showErrorMessageWith(data, status);
                       $modalInstance.close();
                       if (cb) { cb(); }
                   });
                };
            };
            var modalInstance = $modal.open({
                templateUrl: '/static/strider/partials/reinvite-user-modal.html',
                controller: reinviteModalCtrl,
                resolve: {
                    invitee: function() {
                        return invitee;
                    }
                }
            });
        };
    }]);
