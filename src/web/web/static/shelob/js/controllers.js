var shelobControllers = angular.module('shelobControllers', []);

shelobControllers.controller('FileListCtrl', ['$rootScope', '$http', '$log', '$routeParams', '$window', 'API', 'Token',
        function ($scope, $http, $log, $routeParams, $window, API, Token) {

    var oid = typeof $routeParams.oid === "undefined" ? '' : $routeParams.oid;

    // N.B. add a random query param to prevent caching
    API.get('/children/' + oid + '?t=' + Math.random()).then(function(response) {
        // success callback
        $scope.parent = response.data.parent;
        for (var i = 0; i < response.data.folders.length; i++) {
            response.data.folders[i].type = 'folder';
            response.data.folders[i].last_modified = '--';
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
                showErrorMessage("All AeroFS clients are offline. At least one AeroFS desktop client or Team Server must be online to process your request.");
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
                $window.location.replace("/api/v1.0/files/" + oid + "/content?token=" + response.token);
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
                $scope.folders.push(response.data);
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
    // name is displayed as a link
    //
    $scope.cancelRename = function(object) {
        object.edit = false;
        $scope.$apply();
    };
}]);
