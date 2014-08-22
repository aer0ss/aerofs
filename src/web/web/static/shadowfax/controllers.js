var shadowfaxControllers = angular.module('shadowfaxControllers', []);

shadowfaxControllers.controller('SharedFoldersController',
    ['$scope', '$rootScope', '$log', '$modal', '$http',
    function($scope, $rootScope, $log, $modal, $http){
        var csrftoken = $('meta[name=csrf-token]').attr('content');
        $http.defaults.headers.common["X-CSRF-Token"] = csrftoken;
        // paginationLimit set by server, gets inserted into the mako template
        $scope.paginationLimit = paginationLimit;
        $scope.offset = 0;


        var getData = function() {
            // sometimes we have URL param args already, sometimes we don't
            // need this to make sure the offset arg gets added correctly
            if (dataUrl.indexOf("?") == -1) {
                dataUrl = dataUrl + "?";
            }
            $http.get(dataUrl + '&offset=' + $scope.offset.toString()).success(function(response){
                // reset folder data to empty
                $scope.folders = [];
                $scope.leftFolders = [];
                // total number of folders the server knows about
                // number received may be less, due to pagination
                $scope.total = response.total;
                for (var i=0; i < response.data.length; i++) {
                    var folder = response.data[i];
                    folder.people = folder.owners.concat(folder.members);
                    folder.spinnerID = i;
                    if (response.data[i].is_left) {
                        $scope.leftFolders.push(folder);
                    } else {
                        $scope.folders.push(folder);
                    }
                }
                $rootScope.me = response.me;

                $scope.pages = [];
                // do we need pagination? if so, let's count up pages
                if ($scope.total != response.data.length) {
                    for (var j=1; j < Math.ceil($scope.total / $scope.paginationLimit) + 1; j++) {
                        $scope.pages.push(j);
                    }
                }
            }).error(function(response){
                $log.warn('Shared folders data failed to load.');
                showErrorMessageFromResponse(response);
            });
        };
        getData();

        $scope.manage = function(folder) {
            var ManageModalCtrl = function ($scope, $modalInstance, folder) {
              $scope.folder = folder;
              $scope.people = $scope.folder.people;
              $scope.is_privileged = $scope.folder.is_privileged;
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

              var _make_member_request = function(email, permissions, is_multiple, is_last) {
                $log.info("Adding member " + email);
                $http.post(addMemberUrl,
                    {
                        user_id: email,
                        permissions: permissions,
                        store_id: $scope.folder.sid,
                        folder_name: $scope.folder.name,
                        suppress_sharing_rules_warnings: false
                    }
                ).success(function() {
                    if (is_multiple && is_last) {
                        showSuccessMessage('Invitations have been sent.');
                        $scope.invitees = '';
                    } else if (!is_multiple) {
                        showSuccessMessage('Invitation has been sent.');
                        $scope.invitees = '';
                    }
                    var person = $scope.newMember();
                    person.email = email;
                    if (permissions.indexOf('MANAGE') != -1 && permissions.indexOf('WRITE') != -1) {
                        person.is_owner = true;
                    } else if (permissions.indexOf('WRITE') != -1) {
                        person.can_edit = true;
                    }
                    $scope.people.push(person);
                }).error(showErrorMessageFromResponse);
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
                for (var i = 0; i < email_list.length; i++) {
                    _make_member_request(email_list[i],
                        _get_json_permissions(inviteeRole),
                        email_list.length > 1,
                        i === email_list.length - 1);
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
                })
                .error(showErrorMessageFromResponse);
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
            }).error(function(response){
                showErrorMessageFromResponse(response);
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
            }).error(function(response){
                showErrorMessageFromResponse(response);
                spinner.stop();
            });
        };

        $scope.destroyCheck = function(folder) {
            var DestroyModalCtrl = function ($scope, $modalInstance, folder) {
              $scope.folder = folder;

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
                    showSuccessMessage('You have deleted folder "'+ $scope.folder.name +'".');
                    for (var i = 0; i < $scope.folders.length; i++) {
                        if ($scope.folders[i].sid === $scope.folder.sid){
                            $scope.folders.splice(i,1);
                            break;
                        }
                    }
                    $modalInstance.close();
                }).error(showErrorMessageFromResponse);
              };
            };

            var modalInstance = $modal.open({
                templateUrl: '/static/shadowfax/partials/confirm-destroy.html',
                controller: DestroyModalCtrl,
                resolve: {
                    folder: function() {
                        return folder;
                    }
                }
            });
        };

        $scope.getCurrentPage = function() {
            return Math.ceil($scope.offset/$scope.paginationLimit) + 1;
        };

        $scope.showPage = function(pageNum) {
            if (pageNum > 0) {
                // change offset to where the target page will start
                $scope.offset = (pageNum - 1) * $scope.paginationLimit;
                // refresh page data
                getData();
            }
        };
}]);