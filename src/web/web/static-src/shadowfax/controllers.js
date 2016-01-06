var shadowfaxControllers = angular.module('shadowfaxControllers', []);

shadowfaxControllers.controller('SharedFoldersController',
    ['$scope', '$rootScope', '$log', '$modal', '$http',
    function($scope, $rootScope, $log, $modal, $http){
        var csrftoken = $('meta[name=csrf-token]').attr('content');
        // See if this is being requested in IE 8 or lower
        $scope.isOldIE = $('html').is('.ie6, .ie7, .ie8');
        $http.defaults.headers.common["X-CSRF-Token"] = csrftoken;
        $scope.dataUrl = dataUrl;

        var getData = function() {

            var params = {
                offset: $scope.paginationInfo.offset.toString()
            };

            if ($scope.substring) {
                params.substring = $scope.substring;
            }

            $http.get(dataUrl, {
                params: params
            }).success(function(response){

                // total number of folders the server knows about
                // number received may be less, due to pagination
                $scope.paginationInfo.total = response.total;
                $rootScope.me = response.me;
                setFolderData(response.data);
                setCache();
            }).error(function(data, status){
                $log.warn('Shared folders data failed to load.');
                showErrorMessageWith(data, status);
            });
        };

        $scope.initialLoad = {};
        $scope.paginationInfo = {
            active: hasPagination,
            total: 0,
            offset: 0,
            limit: parseInt(paginationLimit),
            callback: function(offset,substring){
                $scope.paginationInfo.offset = offset;
                $scope.substring = substring || '';
                getData();
            }
        };

        var setFolderData = function(data) {

            if (data.hasOwnProperty('folders')) {
                // We are looking at user shared folders

                // Joined Folders
                $scope.folders = data.folders.map(function (folder, i) {
                    folder.spinnerID = i;
                    return folder;
                });

                // Left Folders
                // We want each to have a unique spinnerID, so add the current index
                // to the already existing joined list.
                $scope.leftFolders = data.left_folders.map(function (folder, i) {
                    folder.spinnerID = data.folders.length + i;
                    return folder;
                });
            } else {
                // We are looking at organization shared folders
                $scope.folders = data.map(function (folder, i) {
                    folder.spinnerID = i;
                    return folder;
                });
            }
        }

        var setCache = function() {
            // For the search feature -- we don't want to
            // have to search when user clears entry.
            if (!$scope.initialLoad.folders) {
                $scope.initialLoad.folders = $scope.folders;
            }

            if (!$scope.initialLoad.total) {
                $scope.initialLoad.total = $scope.paginationInfo.total;
            }
        };

        var populateFromCache = function() {
            $scope.paginationInfo.total = $scope.initialLoad.total;
            $scope.folders = $scope.initialLoad.folders;
        };

        getData();

        $scope.updateFolders = function(matches, total, substring) {
            $scope.substring = substring;
            $scope.paginationInfo.total = total;
            setFolderData(matches);
        };

        $scope.restore = function() {
            $scope.substring = '';
            $scope.paginationInfo.offset = 0;

            if ($scope.initialLoad.folders && $scope.initialLoad.folders.length){
                populateFromCache();
            } else {
                getData('Retrieving new page data on clear.');
            }
        };

        $scope.manage = function(folder) {
            var ManageExternalModalCtrl = function ($scope, $modalInstance) {
                $scope.external_cancel = function () {
                    $modalInstance.close();
                };
                $scope.external_ok = function() {
                    $modalInstance.close(true);
                }
            };
            var ManageModalCtrl = function ($scope, $modalInstance, folder) {
              $scope.folder = folder;
              $scope.people = $scope.folder.people;
              $scope.is_privileged = $scope.folder.is_privileged;
              $scope.error = false;
              $scope.canAdminister = canAdminister || false;
              // See if this is being requested in IE 8 or lower
              var isOldIE = $('html').is('.ie6, .ie7, .ie8');
              $scope.getUsersAndGroupsURL = getUsersAndGroupsURL;

              $scope.getGroupMembers = function(group) {
                  $http.get(getGroupMembersURL, {
                      params: {
                          id: group.id
                      }
                  }).success(function(response){
                      group.members = response.members || [];
                  }).error(function(response, status){
                      $log.error(response);
                  });
              };

              // make groups know their own members
              // inside of member management modal
              for (var i = 0; i < $scope.people.length; i++) {
                var entity = $scope.people[i];
                if (entity.is_group) {
                    $scope.getGroupMembers(entity);
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

              var showModalErrorMessage = function(message, response) {
                    if (response && response.status == 403) {
                        $scope.error = message +
                            " It appears you are not authorized to administer this folder. " +
                            "Please try reloading the page or logging back in.";
                    } else if (response && response.message) {
                        $scope.error = response.message;
                    } else {
                        $scope.error = message;
                    }
              };

              var _externalConfirmModal = function () {
                return $modal.open({
                    templateUrl: '/static/shadowfax/partials/confirm-external.html',
                    controller: ManageExternalModalCtrl
                });
              }

              var _make_member_request = function(identifier, entity, permissions, is_group, suppress_sharing_rules_warnings) {
                suppress_sharing_rules_warnings = typeof suppress_sharing_rules_warnings === 'boolean' ? suppress_sharing_rules_warnings : false;
                $http.post(addMemberUrl,
                    {
                        subject_id: identifier,
                        permissions: permissions,
                        is_group: is_group,
                        store_id: $scope.folder.sid,
                        folder_name: $scope.folder.name,
                        suppress_sharing_rules_warnings: suppress_sharing_rules_warnings
                    }
                ).success(function(response) {
                    var newMember = $scope.newMember();
                    if (permissions.indexOf('MANAGE') != -1 && permissions.indexOf('WRITE') != -1) {
                        newMember.is_owner = true;
                    } else if (permissions.indexOf('WRITE') != -1) {
                        newMember.can_edit = true;
                    }
                    newMember.is_group = is_group;
                    if (is_group) {
                        newMember.id = identifier;
                        newMember.name = entity.name;
                        $scope.getGroupMembers(newMember);
                    } else {
                        newMember.email = identifier;
                        newMember.first_name = entity.first_name;
                        newMember.last_name = entity.last_name;
                    }
                    $scope.people.push(newMember);
                }).error(function(response) {
                    if(response.type === "SHARING_RULES_WARNINGS") {
                        var data = (JSON.parse(response.data)).pop();
                        if(data.type === "WARNING_DOWNGRADE") {
                             showModalErrorMessage("This folder is shared with external users. To avoid accidental " +
                                "data leaks, internal Owners are downgraded to Managers, and internal Editors to Viewers.",
                                response);
                                 _make_member_request(identifier, entity, permissions, is_group, true);
                        } else if(data.type === "WARNING_NO_EXTERNAL_OWNERS") {
                             showModalErrorMessage("External Users cannot manage or own a shared folder.",
                                         response);
                        } else if(data.type === "WARNING_EXTERNAL_SHARING"){
                            _externalConfirmModal().result.then(function(inviteExternalUser) {
                                if(inviteExternalUser){
                                    _make_member_request(identifier, entity, permissions, is_group, true);
                                }
                            });
                        } else {
                            showModalErrorMessage("Sorry, the invitation failed. Please try again.", response);
                        }
                    } else {
                        showModalErrorMessage("Sorry, the invitation failed. Please try again.", response);
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
                var permissions = _get_json_permissions(inviteeRole);

                // is_group is only set if we're receiving data from autocomplete
                if (invitee.is_group === true) {
                    for (var i = 0; i < $scope.people.length; i++) {
                        if ($scope.people[i].is_group && $scope.people[i].id == invitee.id) {
                            $log.info("cannot invite group " + invitee.name + " they're already in this folder");
                            $scope.error = "The group " + invitee.name + " is already in this folder."
                            stopModalSpinner();
                            return;
                        }
                    }
                    $log.info("inviting group " + invitee.id + " to folder");
                    _make_member_request(invitee.id, invitee, permissions, true);
                } else if (invitee.is_group === false) {
                    for (var i = 0; i < $scope.people.length; i++) {
                        if ((! $scope.people[i].is_group) && $scope.people[i].email == invitee.email) {
                            $log.info("cannot invite " + invitee.name + " they're already in this folder");
                            $scope.error = invitee.name + " is already in this folder."
                            stopModalSpinner();
                            return;
                        }
                    }
                    $log.info("inviting user " + invitee.email + " to folder");
                    _make_member_request(invitee.email, invitee, permissions, false);
                } else {
                    // no data available from autocomplete, will have to parse a string of emails
                    var splitList = $.trim(invitee.name).split(/\s*[,;()\s]\s*/);
                    var emailList = splitList.filter(function(item){
                        return item.search('@') != -1;
                    });
                    for (var i = 0; i < emailList.length; i++) {
                        $log.info("inviting user " + emailList[i] + " to folder");
                        _make_member_request(emailList[i], {'email' : emailList[i]}, permissions, false);
                    }
                }
                stopModalSpinner();
              };

              $scope.changePerms = function(entity, role) {
                startModalSpinner();

                $log.info("Changing permissions for " + (entity.email || entity.name) + " on folder " + folder.name);
                var _set_perm_request = function (entity, role, suppress_sharing_rules_warnings) {
                    suppress_sharing_rules_warnings = typeof suppress_sharing_rules_warnings === 'boolean' ? suppress_sharing_rules_warnings : false;
                    var identifier = entity.email || entity.id;
                        $http.post(setPermUrl, {
                            store_id: $scope.folder.sid,
                            subject_id: identifier,
                            is_group: entity.is_group,
                            permissions: _get_json_permissions(role),
                            suppress_sharing_rules_warnings: suppress_sharing_rules_warnings
                        }).success(function(response){
                            // casting to boolean
                            entity.is_owner = role === "Owner";
                            entity.can_edit = (role === "Owner" || role === "Editor");
                            stopModalSpinner();
                        }).error(function(response){
                            if(response.type === "SHARING_RULES_WARNINGS") {
                                var data = (JSON.parse(response.data)).pop();
                                if(data.type === "WARNING_DOWNGRADE") {
                                     showModalErrorMessage("This folder is shared with external users. To avoid accidental " +
                                        "data leaks, internal Owners are downgraded to Managers, and internal Editors to Viewers.",
                                        response);
                                } else if(data.type === "WARNING_NO_EXTERNAL_OWNERS") {
                                     showModalErrorMessage("External Users cannot manage or own a shared folder.",
                                        response);
                                }
                            } else {
                                showModalErrorMessage("Sorry, the permissions change failed.", response);
                            }
                        });
                };
                _set_perm_request(entity, role);

                stopModalSpinner();
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
                getData();
                spinner.stop();
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
                getData();
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
                        return function(){
                            getData();
                        };
                    }
                }
            });
        };
}]);
