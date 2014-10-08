var shadowfaxControllers = angular.module('shadowfaxControllers', []);

shadowfaxControllers.controller('SharedFoldersController',
    ['$scope', '$rootScope', '$log', '$modal', '$http',
    function($scope, $rootScope, $log, $modal, $http){
        var csrftoken = $('meta[name=csrf-token]').attr('content');
        $http.defaults.headers.common["X-CSRF-Token"] = csrftoken;

        var getData = function() {
            // sometimes we have URL param args already, sometimes we don't
            // need this to make sure the offset arg gets added correctly
            if (dataUrl.indexOf("?") == -1) {
                dataUrl = dataUrl + "?";
            }
            $http.get(dataUrl, {
                params: {
                    offset: $scope.paginationInfo.offset.toString()
                }
            }).success(function(response){
                // reset folder data to empty
                var folder;
                $scope.folders = [];
                $scope.leftFolders = [];
                // total number of folders the server knows about
                // number received may be less, due to pagination
                $scope.paginationInfo.total = response.total;
                for (var i=0; i < response.data.length; i++) {
                    folder = response.data[i];
                    folder.people = folder.owners.concat(folder.members);
                    folder.spinnerID = i;
                    if (response.data[i].is_left) {
                        $scope.leftFolders.push(folder);
                    } else {
                        $scope.folders.push(folder);
                    }
                }
                $rootScope.me = response.me;
            }).error(function(data, status){
                $log.warn('Shared folders data failed to load.');
                showErrorMessageWith(data, status);
            });
        };
        $scope.paginationInfo = {
            total: 0,
            offset: 0,
            limit: parseInt(paginationLimit, 10),
            callback: function(offset){
                $scope.paginationInfo.offset = offset;
                getData();
            }
        };
        getData();

        $scope.manage = function(folder) {
            var ManageModalCtrl = function ($scope, $modalInstance, folder) {
              $scope.folder = folder;
              $scope.people = $scope.folder.people;
              $scope.is_privileged = $scope.folder.is_privileged;
              $scope.error = false;
              $scope.canAdminister = canAdminister || false;

              $scope.newMember = function(){
                return {
                    email: '',
                    is_pending: true,
                    is_owner: false,
                    can_edit: false
                  };
              };
              $scope.inviteeRole = 'Viewer';

              /* Spinner stuff */
              initializeSpinners();
              function startModalSpinner() {
                startSpinner($('#modal-spinner'), 3);
              }
              function stopModalSpinner() {
                stopSpinner($('#modal-spinner'));
              }

              /* Convert string descriptor of role to
                 permissions list expected by server */
              var _get_json_permissions = function(role) {
                    var permissions = {
                        "Owner": ["MANAGE", "WRITE"],
                        "Editor": ["WRITE"],
                        "Viewer": [],
                        "Manager": ["MANAGE"]
                    };
                    return permissions[role];
              };

              var showModalErrorMessage = function(message) {
                return function(response) {
                    if (response && response.status == 403) {
                        $scope.error = message +
                            " It appears you are not authorized to administer this folder. " +
                            "Please try reloading the page or logging back in.";
                    } else {
                        $scope.error = message + " Please try again.";
                    }
                };
              };

              var _make_member_request = function(email, permissions) {
                $log.info("Adding member " + email);
                $scope.error = false;
                $http.post(addMemberUrl,
                    {
                        user_id: email,
                        permissions: permissions,
                        store_id: $scope.folder.sid,
                        folder_name: $scope.folder.name,
                        suppress_sharing_rules_warnings: false
                    }
                ).success(function() {
                    $scope.invitees = '';
                    var person = $scope.newMember();
                    person.email = email;
                    if (permissions.indexOf('MANAGE') != -1 && permissions.indexOf('WRITE') != -1) {
                        person.is_owner = true;
                    } else if (permissions.indexOf('WRITE') != -1) {
                        person.can_edit = true;
                    }
                    $scope.people.push(person);
                }).error(showModalErrorMessage("Sorry, the invitation failed."));
              };

              $scope.ok = function () {
                $modalInstance.close();
              };

              $scope.inviteMembers = function (invitees, inviteeRole) {
                startModalSpinner();
                var email_list = $.trim(invitees).split(/\s*[,;\s]\s*/).filter(
                        function(item){
                            return item.indexOf('@') != -1;
                        });
                var permissions = _get_json_permissions(inviteeRole);
                for (var i = 0; i < email_list.length; i++) {
                    _make_member_request(email_list[i], permissions);
                }
                stopModalSpinner();
              };

              $scope.changePerms = function(person, role) {
                $log.info("Changing permissions for " + person.email + " on folder " + folder.name);
                startModalSpinner();

                $http.post(setPermUrl, {
                    store_id: $scope.folder.sid,
                    user_id: person.email,
                    permissions: _get_json_permissions(role),
                    suppress_sharing_rules_warnings: false
                }).success(function(response){
                    // casting to boolean
                    person.is_owner = role === "Owner";
                    person.can_edit = (role === "Owner" || role === "Editor");
                    stopModalSpinner();
                }).error(showModalErrorMessage("Sorry, the permissions change failed."));
              };

              $scope.remove = function(person) {
                $log.info("Removing " + person.email + " from folder " + folder.name);
                startModalSpinner();
                $http.post(
                    removeMemberUrl,
                    {
                        user_id: person.email,
                        store_id: $scope.folder.sid
                    }
                )
                .success(function() {
                    for (var i=0; i < $scope.people.length; i++) {
                        if ($scope.people[i].email === person.email) {
                            $scope.people.splice(i,1);
                            break;
                        }
                    }
                    stopModalSpinner();
                })
                .error(function (jqXHR, textStatus, errorThrown) {
                    showErrorMessageUnsafe("Failed to remove member " +
                        person.email + ": " + errorThrown);
                    stopModalSpinner();
                });
              };

            };
            var modalInstance = $modal.open({
                templateUrl: '/static/shadowfax/partials/manage-members.html',
                controller: ManageModalCtrl,
                resolve: {
                    folder: function() {
                        return folder;
                    }
                }
            });
        };

        $scope.leave = function(folder) {
            var spinner = new Spinner(defaultSpinnerOptions).spin();
            $('#folder-' + folder.spinnerID.toString() +
                ' .folder-spinner')[0].appendChild(spinner.el);

            $http.post(leaveFolderUrl,
                {
                    permissions: [],
                    store_id: folder.sid,
                    folder_name: folder.name,
                }
            ).success(function() {
                $log.info('You have left folder '+ folder.name);
                for (var i = 0; i < $scope.folders.length; i++) {
                    if ($scope.folders[i].sid === folder.sid){
                        $scope.leftFolders.push($scope.folders[i]);
                        $scope.folders.splice(i,1);
                        break;
                    }
                }
            }).error(function(data, status){
                showErrorMessageWith(data, status);
                spinner.stop();
            });
        };

        $scope.rejoin = function(folder) {
            var spinner = new Spinner(defaultSpinnerOptions).spin();
            $('#left-folder-' + folder.spinnerID.toString() +
                ' .folder-spinner')[0].appendChild(spinner.el);
            $http.post(rejoinFolderUrl,
                {
                    sid: folder.sid,
                }
            ).success(function(){
                $log.info("You have rejoined folder " + folder.name);
                for (var i = 0; i < $scope.folders.length; i++) {
                    if ($scope.leftFolders[i].sid === folder.sid){
                        $scope.folders.push($scope.leftFolders[i]);
                        $scope.leftFolders.splice(i,1);
                        break;
                    }
                }
            }).error(function(data, status){
                showErrorMessageWith(data, status);
                spinner.stop();
            });
        };

        $scope.destroyCheck = function(folder) {
            var DestroyModalCtrl = function ($scope, $modalInstance, folder, deleteFolder) {
              $scope.folder = folder;
              $scope.deleteFolder = deleteFolder;

              $scope.cancel = function () {
                $modalInstance.close();
              };

              $scope.ok = function () {
                $http.post(destroyFolderUrl,
                    {
                        permissions: [],
                        store_id: $scope.folder.sid,
                        folder_name: $scope.folder.name,
                        user_id: $scope.me,
                    }
                ).success(function(e) {
                    $log.info('You have deleted folder "'+ $scope.folder.name +'".');
                    $scope.deleteFolder($scope.folder);
                    $modalInstance.close();
                }).error(showErrorMessageWith);
              };
            };

            var modalInstance = $modal.open({
                templateUrl: '/static/shadowfax/partials/confirm-destroy.html',
                controller: DestroyModalCtrl,
                resolve: {
                    folder: function() {
                        return folder;
                    },
                    deleteFolder: function() {
                        return function(doomedFolder){
                            for (var i = 0; i < $scope.folders.length; i++) {
                                if ($scope.folders[i].sid === doomedFolder.sid){
                                    $scope.folders.splice(i,1);
                                    break;
                                }
                            }
                        };
                    }
                }
            });
        };
}]);
