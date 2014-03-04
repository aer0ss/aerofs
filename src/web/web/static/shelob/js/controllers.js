var shelobControllers = angular.module('shelobControllers', []);

shelobControllers.controller('FileListCtrl', ['$rootScope', '$http', '$log', '$routeParams', '$window', 'API', 'Token',
        function ($scope, $http, $log, $routeParams, $window, API, Token) {

    var oid = typeof $routeParams.oid === "undefined" ? '' : $routeParams.oid;

    // N.B. add a random query param to prevent caching
    API.get('/children/' + oid + '?t=' + Math.random()).then(function(data) {
        // success callback
        $scope.parent = data.parent;
        $scope.files = data.files;
        $scope.folders = data.folders;
    }, function(status) {
        // failure callback
        $log.error('list children call failed with ' + status);
        if (status == 503) {
            showErrorMessage(getClientsOfflineErrorText());
        } else if (status == 404) {
            showErrorMessage("The folder you requested was not found.");
        } else {
            showErrorMessage(getInternalErrorText());
        }
    });

    if ($scope.breadcrumbs === undefined) $scope.breadcrumbs = [];
    if (oid == '') {
        $scope.breadcrumbs = [];
    } else {
        API.get('/folders/' + oid).then(function(data) {
            // success callback
            var crumb = {id: data.id, name: data.name};
            for (var i = 0; i < $scope.breadcrumbs.length; i++) {
                if ($scope.breadcrumbs[i].id == data.id) {
                    // if the trail reads Home > Folder > Subfolder > SubSubFolder
                    // and the user clicks the link to Folder, remove everything in the
                    // breadcrumb trail from Folder onward
                    $scope.breadcrumbs.splice(i, Number.MAX_VALUE);
                    break;
                }
            }
            // append the folder to the breadcrumb trail
            $scope.breadcrumbs.push(data);
        }, function(status) {
            // failure callback
            $log.error('get folder info call failed with ' + status);
            if (status == 503) {
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
        API.head("/files/" + oid + "/content?t=" + Math.random()).then(function(data) {
            Token.get().then(function(token) {
                // N.B replace(url) will replace the current history with url, whereas
                // assign(url) will append url to the history chain. We use replace() so
                // that a user can navigate from folder Foo to folder Bar, click to download
                // a file, and then use the back button to return to Foo.
                $window.location.replace("/api/v1.0/files/" + oid + "/content?token=" + token);
            }, function(status) {
                // somehow failed to get token despite the fact that a request just succeeded
                showErrorMessage(getInternalErrorText());
            });
        }, function(status) {
            // HEAD request failed
            $log.error('HEAD /files/' + oid + ' failed with status ' + status);
            if (status == 503) {
                showErrorMessage(getClientsOfflineErrorText());
            } else if (status == 404) {
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

    // This is called when a user submits the name for a new folder
    //
    //
    // It should submit an API request to create a folder and update
    // the view, or do nothing if the user did not enter any text.
    //
    // The name that the user entered is at $scope.newFolder.name
    //
    $scope.submitNewFolder = function() {
        $log.debug("new folder: " + $scope.newFolder.name);
        if ($scope.newFolder.name != '') {
            folderData = {name: $scope.newFolder.name, parent: $scope.parent};
            API.post('/folders', folderData).then(function(data) {
                // POST /folders returns the new folder object
                $scope.folders.push(data);
                showSuccessMessage("Successfully created a new folder.");
            }, function(status) {
                // create new folder failed
                if (status == 503) {
                    showErrorMessage(getClientsOfflineErrorText());
                } else if (status == 409) {
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
    }
}]);
