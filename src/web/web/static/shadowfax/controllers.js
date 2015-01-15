var shadowfaxControllers = angular.module('shadowfaxControllers', []);

shadowfaxControllers.controller('SharedFoldersController',
    ['$scope', '$rootScope', '$log', '$modal', '$http',
    function($scope, $rootScope, $log, $modal, $http){
        var csrftoken = $('meta[name=csrf-token]').attr('content');
        // See if this is being requested in IE 8 or lower
        $scope.isOldIE = $('html').is('.ie6, .ie7, .ie8');
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
                    folder.people = folder.owners.concat(folder.members).concat(folder.groups);
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
            active: hasPagination,
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
              // See if this is being requested in IE 8 or lower
              var isOldIE = $('html').is('.ie6, .ie7, .ie8');
              $scope.getUsersAndGroupsURL = getUsersAndGroupsURL;

              // make groups know their own members
              // inside of member management modal
              var getGroupMembers = function(entity) {
                $http.get(getGroupMembersURL, {
                    params: {
                        id: entity.id
                    }
                }).success(function(response){
                    entity.members = response.members || [];
                }).error(function(response, status){
                    $log.error(response);
                });
              };
              for (var i = 0; i < $scope.people.length; i++) {
                var entity = $scope.people[i];
                if (entity.is_group) {
                    getGroupMembers(entity);
                }
              }

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

              var _make_member_request = function(identifier, permissions, group_name) {
                $log.info("Adding member " + identifier);
                $scope.error = false;
                $http.post(addMemberUrl,
                    {
                        subject_id: identifier,
                        permissions: permissions,
                        is_group: !!group_name,
                        store_id: $scope.folder.sid,
                        folder_name: $scope.folder.name,
                        suppress_sharing_rules_warnings: false
                    }
                ).success(function(response) {
                    $scope.invitees = '';
                    var entity = $scope.newMember();
                    if (permissions.indexOf('MANAGE') != -1 && permissions.indexOf('WRITE') != -1) {
                        entity.is_owner = true;
                    } else if (permissions.indexOf('WRITE') != -1) {
                        entity.can_edit = true;
                    }
                    if (group_name) {
                        entity.id = identifier;
                        entity.members = [];
                        $http.get(getGroupMembersURL, {
                            params: {
                              id: entity.id
                            }
                        }).success(function(response){
                           entity.members = response.members;
                        }).error(function(response, status){
                            $log.error(status);
                            $log.error(response);
                        });
                        entity.name = group_name;
                        entity.is_group = true;
                    } else {
                        entity.email = identifier;
                    }
                    $scope.people.push(entity);
                }).error(showModalErrorMessage("Sorry, the invitation failed."));
              };

              $scope.ok = function () {
                $modalInstance.close();
              };

              $scope.inviteMembers = function (inviteeString, inviteeRole) {
                startModalSpinner();
                var splitList = $.trim(inviteeString).split(/\s*[,;()]\s*/);
                var emailList = splitList.filter(function(item){
                    return item.split("").indexOf('@') != -1;
                });

                // Look up non email address to see if there's one and only one
                // group with that name and they're not already invited
                // If so, invite it to the folder!
                var nonEmailList = splitList.filter(
                    function(groupName){
                        if (groupName.indexOf('@') != -1) {
                            // TODO (RD) currently no guarantee on what characters a group name has
                            return false;
                        }
                        for (var j = 0; j < $scope.people.length; j++) {
                            if ($scope.people[j].name === groupName) {
                                // TODO: error message
                                // telling people the group's already invited
                                return false;
                            }
                        }
                        return true;
                    });

                var permissions = _get_json_permissions(inviteeRole);

                for (var i = 0; i < nonEmailList.length; i++) {
                    // TODO (RD) should already have groupID, don't need to make separate request for it
                    $http.get(getGroupsURL, {
                        params: {
                            substring: nonEmailList[i]
                        }
                    }).then(function(res) {
                        if (res.data.groups.length === 1) {
                            var group = res.data.groups[0];
                            $log.info("Invite group " + group.name + " to folder.");
                            _make_member_request(group.id, permissions, group.name);
                        }
                    });
                }

                for (var i = 0; i < emailList.length; i++) {
                    _make_member_request(emailList[i], permissions);
                }
                stopModalSpinner();
              };

              $scope.changePerms = function(entity, role) {
                startModalSpinner();

                var identifier = entity.email || entity.id;
                $log.info("Changing permissions for " + (entity.email || entity.name) + " on folder " + folder.name);

                $http.post(setPermUrl, {
                    store_id: $scope.folder.sid,
                    subject_id: identifier,
                    is_group: entity.is_group,
                    permissions: _get_json_permissions(role),
                    suppress_sharing_rules_warnings: false
                }).success(function(response){
                    // casting to boolean
                    entity.is_owner = role === "Owner";
                    entity.can_edit = (role === "Owner" || role === "Editor");
                    stopModalSpinner();
                }).error(showModalErrorMessage("Sorry, the permissions change failed."));
              };

              $scope.remove = function(entity) {
                var identifier = entity.email || entity.id;
                $log.info("Removing " + (entity.email || entity.name) + " from folder " + folder.name);
                startModalSpinner();
                $http.post(
                    removeMemberUrl,
                    {
                        subject_id: identifier,
                        is_group: entity.is_group,
                        store_id: $scope.folder.sid
                    }
                )
                .success(function() {
                    for (var i=0; i < $scope.people.length; i++) {
                        if (($scope.people[i].email || $scope.people[i].id) === identifier) {
                            $scope.people.splice(i,1);
                            break;
                        }
                    }
                    stopModalSpinner();
                })
                .error(function (jqXHR, textStatus, errorThrown) {
                    showErrorMessageUnsafe("Failed to remove member " +
                        identifier + ": " + errorThrown);
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
                for (var i = 0; i < $scope.leftFolders.length; i++) {
                    if ($scope.leftFolders[i].sid === folder.sid){
                        $scope.folders.push($scope.leftFolders[i]);
                        $scope.leftFolders.splice(i,1);
                        break;
                    }
                }
                spinner.stop();
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
