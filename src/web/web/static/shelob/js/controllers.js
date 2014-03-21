var shelobControllers = angular.module('shelobControllers', ['shelobConfig']);

shelobControllers.controller('FileListCtrl', ['$rootScope', '$http', '$log', '$routeParams', '$window', '$modal', 'API', 'Token', 'RootId', 'API_LOCATION',
        function ($scope, $http, $log, $routeParams, $window, $modal, API, Token, RootId, API_LOCATION) {

    var FOLDER_LAST_MODIFIED = '--';

    var oid = typeof $routeParams.oid === "undefined" ? '' : $routeParams.oid;

    // N.B. add a random query param to prevent caching
    API.get('/children/' + oid + '?t=' + Math.random()).then(function(response) {
        // success callback
        $scope.parent = response.data.parent;
        if (oid == '') RootId.set($scope.parent);
        for (var i = 0; i < response.data.folders.length; i++) {
            response.data.folders[i].type = 'folder';
            response.data.folders[i].last_modified = FOLDER_LAST_MODIFIED;
        }
        for (var i = 0; i < response.data.files.length; i++) {
            response.data.files[i].type = 'file';
        }
        $scope.objects = response.data.folders.concat(response.data.files);
    }, function(response) {
        // failure callback
        $log.error('list children call failed with ' + response.status);
        if (response.status == 503) {
            showErrorMessage(getClientsOfflineErrorText());
        } else if (response.status == 404) {
            showErrorMessage("The folder you requested was not found.");
        } else {
            showErrorMessage(getInternalErrorText());
        }
    });

    if ($scope.breadcrumbs === undefined) $scope.breadcrumbs = [];
    if (oid == '') {
        $scope.breadcrumbs = [];
    } else {
        API.get('/folders/' + oid).then(function(response) {
            // success callback
            var crumb = {id: response.data.id, name: response.data.name};
            for (var i = 0; i < $scope.breadcrumbs.length; i++) {
                if ($scope.breadcrumbs[i].id == response.data.id) {
                    // if the trail reads Home > Folder > Subfolder > SubSubFolder
                    // and the user clicks the link to Folder, remove everything in the
                    // breadcrumb trail from Folder onward
                    $scope.breadcrumbs.splice(i, Number.MAX_VALUE);
                    break;
                }
            }
            // append the folder to the breadcrumb trail
            $scope.breadcrumbs.push(response.data);
        }, function(response) {
            // failure callback
            $log.error('get folder info call failed with ' + response.status);
            if (response.status == 503) {
                showErrorMessage(getClientsOfflineErrorText());
            } else {
                showErrorMessage(getInternalErrorText());
            }
        });
    }

    // This is called when a user clicks on a link to a file
    //
    // It should direct the browser to download the file if possible, or show
    // an appropriate error message.
    //
    // Arguments:
    //   oid: the oid of the file to download
    //
    $scope.download = function(oid) {
        // Do a HEAD request for the file, and proceed only if the HEAD returns 2xx
        // We do this so that we can handle errors before directing the user away from
        // Shelob to the API
        //
        // N.B. add a random query param to prevent caching
        API.head("/files/" + oid + "/content?t=" + Math.random()).then(function(response) {
            $log.info('HEAD /files/' + oid + '/content succeeded');
            Token.get().then(function(response) {
                // N.B replace(url) will replace the current history with url, whereas
                // assign(url) will append url to the history chain. We use replace() so
                // that a user can navigate from folder Foo to folder Bar, click to download
                // a file, and then use the back button to return to Foo.
                $window.location.replace(API_LOCATION + "/api/v1.0/files/" + oid + "/content?token=" + response.token);
            }, function(response) {
                // somehow failed to get token despite the fact that a request just succeeded
                showErrorMessage(getInternalErrorText());
            });
        }, function(response) {
            // HEAD request failed
            $log.error('HEAD /files/' + oid + '/content failed with status ' + response.status);
            if (response.status == 503) {
                showErrorMessage(getClientsOfflineErrorText());
            } else if (response.status == 404) {
                showErrorMessage("The file you requested was not found.");
            } else {
                showErrorMessage(getInternalErrorText());
            }
        });
    };

    // data controlling the "new folder" table row and input
    $scope.newFolder = {
        hidden: true,
        name: '',
    };

    // data controlling which object is moused over, and should display action icons
    $scope.mousedOver = {
        object: null
    };

    // This is called when a user submits the name for a new folder
    //
    // It should submit an API request to create a folder and update
    // the view, or do nothing if the user did not enter any text.
    //
    // The name that the user entered is at $scope.newFolder.name
    //
    $scope.submitNewFolder = function() {
        $log.debug("new folder: " + $scope.newFolder.name);
        if ($scope.newFolder.name != '') {
            var folderData = {name: $scope.newFolder.name, parent: $scope.parent};
            API.post('/folders', folderData).then(function(response) {
                // POST /folders returns the new folder object
                $scope.objects.push({
                    type: 'folder',
                    id: response.data.id,
                    name: response.data.name,
                    last_modified: FOLDER_LAST_MODIFIED,
                    is_shared: response.data.is_shared
                });
                showSuccessMessage("Successfully created a new folder.");
            }, function(response) {
                // create new folder failed
                if (response.status == 503) {
                    showErrorMessage(getClientsOfflineErrorText());
                } else if (response.status == 409) {
                    showErrorMessage("A file or folder with that name already exists.");
                } else {
                    showErrorMessage(getInternalErrorText());
                }
            });
        } else {
            showErrorMessage('Enter a name for the new folder');
        }
        $scope.cancelNewFolder();
    };

    // This is called when a user presses escape while entering the name of a
    // new folder.
    //
    // It should reset the new folder view.
    //
    $scope.cancelNewFolder = function() {
        $scope.newFolder.hidden = true;
        $scope.newFolder.name = '';
        $scope.$apply();
    };

    // This is called when a user clicks the "rename" action icon
    //
    // It should replace the object's name with a text input initialized with
    // the object's name.
    //
    $scope.startRename = function(object) {
        object.newName = object.name;
        object.edit = true;
    };

    // This is called when a user submits a new name for an object
    //
    // It should attempt to rename the object if the new name is different, and handle
    // success and failure scenarios. In any case, it should reset the view at the end
    // so that the input is hidden and the object's name is displayed as a link.
    //
    $scope.submitRename = function(object) {
        $log.info("attempting rename from " + object.name + " to " + object.newName);
        if (object.name == object.newName || object.newName == "") {
            $scope.cancelRename(object);
            return;
        }
        var path = '/' + object.type + 's/' + object.id;
        API.put(path, {parent: $scope.parent, name: object.newName}).then(function(response) {
            // rename succeeded
            object.name = response.data.name;
            if (response.data.last_modified) object.last_modified = response.data.last_modified;
            showSuccessMessage("Successfully renamed " + object.type);
        }, function(response) {
            // rename failed
            if (response.status == 503) {
                showErrorMessage(getClientsOfflineErrorText());
            } else if (response.status == 409) {
                showErrorMessage("A file or folder with that name already exists.");
            } else {
                showErrorMessage(getInternalErrorText());
            }
        }).finally(function() {
            // after success or failure
            object.edit = false;
        });
    };

    // This is called when a user presses Escape while renaming an object
    //
    // It should reset the view so that the input is hidden and the object's
    // name is displayed as a link.
    //
    $scope.cancelRename = function(object) {
        object.edit = false;
        $scope.$apply();
    };

    // This is called when a user chooses a destination for an object and
    // confirms that they would like to move it.
    //
    // It should attempt to perform the move action and update the view.
    $scope.submitMove = function(object, destination) {

        var _submitMove = function(object, destination) {
            var data = {parent: destination.id, name: object.name};
            API.put('/' + object.type + 's/' + object.id, data).then(function(response) {
                for (var i = 0; i < $scope.objects.length; i++) {
                    if ($scope.objects[i].id == object.id) {
                        $scope.objects.splice(i, 1);
                        break;
                    }
                }
                showSuccessMessage("Successfully moved to " + destination.label);
            }, function(response) {
                // move failed
                if (response.status == 503) {
                    showErrorMessage(getClientsOfflineErrorText());
                } else if (response.status == 409) {
                    showErrorMessage("A file or folder with that name already exists.");
                } else {
                    showErrorMessage(getInternalErrorText());
                }
            });
        };

        // exit early if move is a no-op
        if (destination.id == oid) return;

        // exit early if you try to move a folder into itself
        if (destination.id == object.id) {
            showErrorMessage("A folder cannot be moved into itself.");
            return;
        }

        // moving under the root anchor requires a separate case because the id
        // of the root anchor may not be known. This branching and the RootId
        // service will go away with API v1.2 where the alias "root" will work as
        // the anchor's ID everywhere.
        if (destination.id == '') {
            RootId.get().then(function(rootId) {
                _submitMove(object, {id: rootId, label: "AeroFS"});
            }, function(response) {
                if (response.status == 503) showErrorMessage(getClientsOfflineErrorText());
                else showErrorMessage(getInternalErrorText());
            });
        } else {
            _submitMove(object, destination);
        }
    };

    // This is called when a user clicks the move icon on a file/folder
    //
    // It should open a modal with a tree view of the AeroFS folder and
    // allow the user to select a destination folder or cancel with no action
    //
    $scope.startMove = function(object) {
        // treedata is a list of folder objects with label, id, and children attrs,
        // where children is a treedata object
        $scope.treedata = [{label: "AeroFS", id: '', children: []}];

        $scope.moveModal = $modal.open({
            templateUrl: '/static/shelob/partials/object-move-modal.html'
        });

        $scope.moveModal.result.then(function(folder) {
            // user submitted the move request
            $log.debug("move submitted", folder);
            $scope.submitMove(object, folder);
        }, function() {
            // user cancelled the move request
            $log.debug("move cancelled");
        });
    };

    // This is called when a user expands a folder in the modal treeview
    //
    // It should fetch the children of that folder if necessary, and update
    // the treeview
    //
    $scope.onMoveFolderExpanded = function(folder) {
        //TODO: don't GET /children if we already know there are no children
        if (folder.children.length > 0) return;
        API.get('/children/' + folder.id + '?t=' + Math.random()).then(function(response) {
            // get children succeeded
            for (var i = 0; i < response.data.folders.length; i++) {
                $log.debug('adding child');
                folder.children.push({
                    label: response.data.folders[i].name,
                    id: response.data.folders[i].id,
                    children: []
                });
            }
        }, function(response) {
            // get children failed
            $log.error("fetching children failed");
            if (response.status == 503) showErrorMessage(getClientsOfflineErrorText());
            else showErrorMessage(getInternalErrorText());
        });
    }
}]);
