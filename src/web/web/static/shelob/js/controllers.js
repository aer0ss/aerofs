var shelobControllers = angular.module('shelobControllers', ['shelobConfig']);

shelobControllers.controller('FileListCtrl', ['$scope',  '$rootScope', '$http', '$log', '$routeParams', '$window', '$modal', '$sce', 'API', 'Token', 'API_LOCATION', 'IS_PRIVATE', 'OutstandingRequestsCounter',
        function ($scope, $rootScope, $http, $log, $routeParams, $window, $modal, $sce, API, Token, API_LOCATION, IS_PRIVATE, OutstandingRequestsCounter) {

    var FOLDER_LAST_MODIFIED = '--';

    // Set csrf-token header on $http by default
    var metas = document.getElementsByTagName('meta');
    for (var i=0; i<metas.length; i++) {
       if (metas[i].getAttribute("name") === "csrf-token") {
          $http.defaults.headers.common['X-CSRF-Token'] = metas[i].getAttribute("content");
       }
    }

    // bind $scope.outstandingRequests to the result of OutstandingRequestsCounter.get()
    // with a $watch so that the scope variable is updated when the result of the service
    // method changes
    $scope.$watch(function() { return OutstandingRequestsCounter.get(); }, function(val) {
        $scope.outstandingRequests = val;
        if (val > 0) angular.element(document.querySelector('html')).addClass('waiting');
        else angular.element(document.querySelector('html')).removeClass('waiting');
    });

    /*
        * * *
        Initialize variables!
        * * *
    */
    // $rootScope.linkPasswordEntered contains user-entered password attempt,
    // if linkshare is password-protected. Nothing, otherwise. Uses $rootScope so that
    // state can be shared with the login controller. Hopefully this can be reformulated in
    // the future.
    // $scope.objects contains all folder/file data.
    // $scope.links contains all link data. Added to $scope.objects by _populateLinks().
    $rootScope.linkPasswordEntered, $scope.objects, $scope.links;

    // See if linksharing has been turned off
    $scope.enableLinksharing = enableLinksharing;
    // See if this is being requested in IE 8 or lower
    $scope.isOldIE = $('html').is('.ie6, .ie7, .ie8');
    // Headers for request, in case user isn't logged in
    // and requests a linkshare page
    $scope.requestHeaders = {};
    $scope.rootFolder = 'root';
    $scope.fsLocation = {
        currentFolder: {
            id: $routeParams.oid || 'root'
        },
    };
    $scope.currentShare = {
        token: null,
        link: null,
        isSingleObject: false,
        file: null,
        folder: null,
        isAdmin: false
    };
    // email address of logged-in user, if any
    $scope.user = currentUser;
    // for anchor SID/OID: return root folder SID/OID
    // for folder: return input
    $scope.deref_anchor = function(folder) {
        if (folder.is_shared) return folder.sid + "00000000000000000000000000000000";
        else return folder.id;
    };

    var _getFolders = function (){
        OutstandingRequestsCounter.push();
        API.get('/folders/' + $scope.fsLocation.currentFolder.id + '?fields=children,path&t=' + Math.random(),
            $scope.requestHeaders).then(function(response) {
            OutstandingRequestsCounter.pop();
            // this is used as the last element of the breadcrumb trail
            $scope.fsLocation.currentFolder.name = response.data.name;

            // omit the root AeroFS folder from the breadcrumb trail, since we will always
            // include a link to the root with the label "My Files"
            if (response.data.path.folders.length && response.data.path.folders[0].name == 'AeroFS') {
                $scope.breadcrumbs = response.data.path.folders.slice(1);
            } else {
                $scope.breadcrumbs = response.data.path.folders.slice(0);
            }
            // Don't show breadcrumbs for folders outside the domain of the share
            if ($scope.currentShare.token) {
                if ($scope.fsLocation.currentFolder.id === $scope.rootFolder) {
                    // populate the folder data of the current share
                    $scope.currentShare.folder = $scope.fsLocation.currentFolder;
                    // save parents
                    $scope.fsLocation.parents = response.data.path.folders.slice(0);
                    $scope.breadcrumbs = [];
                    _detectOwnership($scope.currentShare.folder.id, function(isOwned){
                        $scope.currentShare.isAdmin = isOwned;
                    });
                } else {
                    $scope.fsLocation.parents = $scope.breadcrumbs;
                    for (var i = 0; i < $scope.breadcrumbs.length; i++) {
                        if ($scope.breadcrumbs[i].id === $scope.rootFolder) {
                            $scope.breadcrumbs = $scope.breadcrumbs.slice(i);
                            break;
                        }
                    }
                }
            }

            // set object.type and object.last_modified for files and folders and concat the lists
            for (var i = 0; i < response.data.children.folders.length; i++) {
                response.data.children.folders[i].type = 'folder';
                response.data.children.folders[i].last_modified = FOLDER_LAST_MODIFIED;
            }
            for (var i = 0; i < response.data.children.files.length; i++) {
                response.data.children.files[i].type = 'file';
            }

            $scope.objects = response.data.children.folders.concat(response.data.children.files);

            // check if link data's done loading, if so, populate link data
            // (if not, will populate via the _getLinks call once it's done,
            // or, if $scope.enableLinksharing is false, won't run at all)
            if ($scope.links) {
                _populateLinks();
            }

        }, function(response) {
            OutstandingRequestsCounter.pop();
            if ($scope.currentShare.token && response.status == 404) {
                /* Maybe the request is for a file, not a folder! */
                $log.info('Request may be for a file rather than a folder, retrying...');
                API.get('/files/' + $scope.fsLocation.currentFolder.id + '?fields=children,path&t=' + Math.random(),
                    $scope.requestHeaders).then(function(response) {
                        $scope.currentShare.isSingleObject = true;
                        var object = response.data;
                        object.type = 'file';
                        $scope.currentShare.file = object;
                        _detectOwnership($scope.currentShare.file.parent, function(isOwned){
                            $scope.currentShare.isAdmin = isOwned;
                        });
                        $scope.objects = [$scope.currentShare.file];
                    }, _handleFailure);
            } else {
                _handleFailure(response);
            }
        });
    };

    // Get link sharing data, check if objects are done loading
    // Note: if this is a linksharing page, we don't need this data
    var _getLinks = function (){
        var sid = $scope.fsLocation.currentFolder.id;
        if (sid !== "root") {
            sid = sid.toString().slice(0,sid.length/2);
        }
        OutstandingRequestsCounter.push();
        // TODO: caching
        // links are fetched per store, no need to re-fetch every time the current folder is changed
        API.get('/shares/' + sid + '/urls', $scope.requestHeaders, {version: '1.3'}).then(function(response){
            OutstandingRequestsCounter.pop();
            // check if object data's done loading, if so, populate link data
            // (if not, will populate via the _getFolders call once it's done)
            $scope.links = response.data.urls;
            if ($scope.objects) {
                _populateLinks();
            }
        }, function(response){
            OutstandingRequestsCounter.pop();
            $log.debug("Link data failed to load.");
        });
    };

    // given a folder ID, check if it/its parent is owned by the current user
    // send result to callback function
    var _detectOwnership = function(id, callback) {
        // have to be an owner to make or manage a link
        var linkPerm = "MANAGE";
        if ($scope.user) {
            API.get('/shares/' + id.slice(0,32) + '/members/me?t=' + Math.random(),
                {}).then(function(response){
                    if (response.data.permissions.indexOf(linkPerm) != -1) {
                        $log.info('The current user owns this linkshare.');
                        callback(true);
                    } else {
                        callback(false);
                    }
                }, function(response){
                    $log.error(response);
                    callback(false);
                });
        } else {
            callback(false);
        }
    };

    var _handleFailure = function(response) {
        var status = response.status;
        if (status == 401 && $scope.currentShare.token) {
            // redirect to linkshare login page
            $log.info('Link may be password-protected; directing user to log in.');
            if (window.location.hash != '#/login') {
                window.location.hash = '#/login';
            }
            if ($rootScope.linkPasswordEntered) {
                showErrorMessage('Password was incorrect. Please try again.');
            }
            $rootScope.linkPasswordEntered = undefined;
        } else {
            if ($scope.currentShare.token) {
                $log.error('Link-sharing call failed with ' + status);
            } else {
                $log.error('My Files call failed with ' + status);
            }
            if (status == 503 && $scope.currentShare.token) {
                showErrorMessageUnsafe("This file is currently unavailable because <a href='https://support.aerofs.com/hc/en-us/articles/203143390'>all sharer AeroFS clients are offline</a>. " +
                "Please check with the person who shared this link.");
            } else if (status == 503) {
                $log.info('None of your AeroFS clients are currently online, or API access is disabled.');
                $scope.error = {
                    status: 503,
                    text: $sce.trustAsHtml(getClientsOfflineErrorText(IS_PRIVATE))
                };
            } else if (status == 401) {
                showErrorMessage("Authorization failure; please login again.");
            } else if (status == 404 || status == 400) {
                if ($scope.currentShare.token) {
                    showErrorMessage("This file-sharing link either does not exist or has expired.");
                } else {
                    showErrorMessage("The file you requested was not found.");
                }
            } else {
                showErrorMessageFromResponse(response);
            }
        }
    };

    /* Is this a link sharing page?
       Note: if linkshare page path changes from /l/<whatever>, this will break.
       See url_sharing_view.py for more details! */
    var pathList = window.location.pathname.split('/');
    if (pathList.length > 1 && pathList[1] === "l") {
        $scope.currentShare.token = pathList[2];
    }
    // Different behavior for linkshare pages and my files page
    if ($scope.currentShare.token) {
        // is linksharing turned off?
        if ($scope.enableLinksharing) {
            // ping get_url_info for link's password-needed, expiry, token, and soid
            // if login's already run, and successfully, there'll be a password attached
            $http.post('/url_info/' + $scope.currentShare.token, {
                password: $rootScope.linkPasswordEntered
            }).success(function(response){
                console.log(response);
                $scope.currentShare.link = response;

                $scope.requestHeaders.Authorization = response.Authorization;
                $scope.requestHeaders.token = response.token;
                // linkshare root is never 'root', fixing it
                if ($scope.rootFolder === 'root') {
                    $scope.rootFolder = response.soid;
                }
                if ($scope.fsLocation.currentFolder.id === 'root') {
                    $scope.fsLocation.currentFolder.id = response.soid;
                }
                // manual expiration checking
                if (response.expires < 0) {
                    _handleFailure({status: 404});
                } else {
                    // ping API for child folder / file data
                    _getFolders();
                }
            }).error(function(response, status){
                /* If 401, will redirect to login
                   Otherwise, shows error message. */
                _handleFailure({
                    status: status
                });
            });
        } else {
            // links created before admin turned off linksharing
            // will behave as if they have expired
            _handleFailure({
                status: 400
            });
        }
    } else {
        _getFolders();
        if ($scope.enableLinksharing) {
            _getLinks();
        }
    }

    // This should find an object with a given view and remove it from the current view
    function _remove_by_id(id) {
        for (var i = 0; i < $scope.objects.length; i++) {
            if ($scope.objects[i].id == id) {
                $scope.objects.splice(i, 1);
                return;
            }
        }
    }

    // Remove linksharing link with given key from given file or folder object
    $scope.removeLink = function(object, key){
        for (var i=0; i<object.links.length; i++) {
            if (object.links[i].key === key) {
                object.links.splice(i,1);
                if (object.links.length < 1) {
                    object.showingLinks = false;
                }
                return;
            }
        }
    };

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
        API.head("/files/" + oid + "/content?t=" + Math.random(), $scope.requestHeaders).then(function(response) {
            $log.info('HEAD /files/' + oid + '/content succeeded');
            if ($scope.requestHeaders.token) {
                $window.location.replace(API_LOCATION + "/api/v1.2/files/" + oid +
                    "/content?token=" + $scope.requestHeaders.token);
            } else {
                Token.get().then(function(token) {
                    // N.B replace(url) will replace the current history with url, whereas
                    // assign(url) will append url to the history chain. We use replace() so
                    // that a user can navigate from folder Foo to folder Bar, click to download
                    // a file, and then use the back button to return to Foo.
                    $window.location.replace(API_LOCATION + "/api/v1.2/files/" + oid + "/content?token=" + token);
                }, function(response) {
                    // somehow failed to get token despite the fact that a request just succeeded
                    showErrorMessageUnsafe(getInternalErrorText());
                });
            }
        }, function(response) {
            // HEAD request failed
            $log.error('HEAD /files/' + oid + '/content failed with status ' + response.status);
            if (response.status == 503) {
                showErrorMessageUnsafe(getClientsOfflineErrorText(IS_PRIVATE));
            } else if (response.status == 404) {
                showErrorMessage("The file you requested was not found.");
            } else {
                showErrorMessageUnsafe(getInternalErrorText());
            }
        });
    };

    // data controlling the "new folder" table row and input
    $scope.newFolder = {
        hidden: true,
        name: ''
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
        if ($scope.newFolder.name !== '') {
            var folderData = {name: $scope.newFolder.name, parent: $scope.fsLocation.currentFolder.id};
            API.post('/folders', folderData).then(function(response) {
                // POST /folders returns the new folder object
                $scope.objects.push({
                    type: 'folder',
                    id: response.data.id,
                    name: response.data.name,
                    last_modified: FOLDER_LAST_MODIFIED,
                    is_shared: response.data.is_shared,
                    links: []
                });
            }, function(response) {
                // create new folder failed
                if (response.status == 503) {
                    showErrorMessageUnsafe(getClientsOfflineErrorText(IS_PRIVATE));
                } else if (response.status == 409) {
                    showErrorMessage("A file or folder with that name already exists.");
                } else {
                    showErrorMessageUnsafe(getInternalErrorText());
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
        $log.info("Attempting rename from " + object.name + " to " + object.newName);
        if (object.name == object.newName || object.newName === "") {
            $scope.cancelRename(object);
            return;
        }
        var path = '/' + object.type + 's/' + object.id;
        API.put(path, {parent: $scope.fsLocation.currentFolder.id, name: object.newName}).then(function(response) {
            // rename succeeded
            object.name = response.data.name;
            if (response.data.last_modified) object.last_modified = response.data.last_modified;
        }, function(response) {
            // rename failed
            if (response.status == 503) {
                showErrorMessageUnsafe(getClientsOfflineErrorText(IS_PRIVATE));
            } else if (response.status == 409) {
                showErrorMessage("A file or folder with that name already exists.");
            } else {
                showErrorMessageUnsafe(getInternalErrorText());
            }
        })["finally"](function() {
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
    //
    $scope.submitMove = function(object, destination) {

        // exit early if move is a no-op
        if (destination.id == $scope.fsLocation.currentFolder.id) return;

        // exit early if you try to move a folder into itself
        if (destination.id == object.id) {
            showErrorMessage("A folder cannot be moved into itself.");
            return;
        }

        var data = {parent: destination.id, name: object.name};
        API.put('/' + object.type + 's/' + object.id, data).then(function(response) {
            _remove_by_id(object.id);
        }, function(response) {
            // move failed
            if (response.status == 503) {
                showErrorMessageUnsafe(getClientsOfflineErrorText(IS_PRIVATE));
            } else if (response.status == 409) {
                showErrorMessage("A file or folder with that name already exists.");
            } else {
                showErrorMessageUnsafe(getInternalErrorText());
            }
        });
    };

    // This is called when a user clicks the move icon on a file/folder
    //
    // It should open a modal with a tree view of the AeroFS folder and
    // allow the user to select a destination folder or cancel with no action
    //
    $scope.startMove = function(object) {
        // treedata is a list of folder objects with label, id, and children attrs,
        // where children is a treedata object
        var rootFolder = {label: "AeroFS", id: 'root', children: []};
        // $rootScope used so that the tree control directive can access it
        $rootScope.treedata = [rootFolder];
        $rootScope.onMoveFolderExpanded(rootFolder);

        $scope.moveModal = $modal.open({
            templateUrl: '/static/shelob/partials/object-move-modal.html',
        });

        $scope.moveModal.result.then(function(folder) {
            // user submitted the move request
            $log.debug("Move submitted.", folder);
            $scope.submitMove(object, folder);
        }, function() {
            // user cancelled the move request
            $log.debug("Move cancelled.");
        });
    };

    // This is called when a user expands a folder in the modal treeview
    //
    // It should fetch the children of that folder if necessary, and update
    // the treeview
    //
    // $rootScope used so that the tree control directive can access it
    $rootScope.onMoveFolderExpanded = function(folder) {
        //TODO: don't GET children if we already know there are no children
        if (folder.children.length > 0) return;
        API.get('/folders/' + folder.id + '/children?t=' + Math.random()).then(function(response) {
            // get children succeeded
            for (var i = 0; i < response.data.folders.length; i++) {
                $log.debug('Adding child.');
                folder.children.push({
                    label: response.data.folders[i].name,
                    id: response.data.folders[i].id,
                    children: []
                });
            }
        }, function(response) {
            // get children failed
            $log.error("Fetching children failed.");
            if (response.status == 503) showErrorMessageUnsafe(getClientsOfflineErrorText(IS_PRIVATE));
            else showErrorMessageUnsafe(getInternalErrorText());
        });
    };

    // This is called when a user confirms that they wish to delete an object
    //
    // It should attempt to perform the delete action and update the view.
    //
    $scope.submitDelete = function(object) {
        API['delete']('/' + object.type + 's/' + object.id).then(function(response) {
            _remove_by_id(object.id);
        }, function(response) {
            // failed to delete
            $log.error("Deleting object failed: ", object.id);
            if (response.status == 503) showErrorMessageUnsafe(getClientsOfflineErrorText(IS_PRIVATE));
            else if (response.status == 404) showErrorMessage("The " + object.type + " you requested was not found.");
            else showErrorMessageUnsafe(getInternalErrorText());
        });
    };

    // This is called when a user clicks the delete icon on a file/folder
    //
    // It should open a modal asking the user to confirm the deletion
    //
    $scope.startDelete = function(object) {
        $scope.deleteModal = $modal.open({
            templateUrl: '/static/shelob/partials/object-delete-modal.html',
            controller: function($scope) {
                $scope.name = object.name;
            }
        });

        $scope.deleteModal.result.then(function() {
            // user confirmed the delete
            $log.debug("Delete confirmed", object);
            $scope.submitDelete(object);
        }, function() {
            // user cancelled the delete request
            $log.debug("Delete cancelled.");
        });
    };

    /* Link-based sharing methods */
    // attaches links to their associated files and folders
    function _populateLinks() {
        var o, l;
        for (var i = 0; i < $scope.objects.length; i++) {
            o = $scope.objects[i];
            o.links = [];
            var links = $scope.links;
            for (var j = 0; j < $scope.links.length; j++) {
                l = $scope.links[j];
                // Only show links that haven't already expired
                if (l.soid === o.id && l.expires > -1) {
                    if (l.has_password) {
                        l.password = '********';
                    }
                    o.links.push(l);
                    links.splice(j,1);
                }
            }
            // remove placed links from the list
            $scope.links = links;
        }
    }

    // Needed to print the sharing URL
    $scope.hostname = window.location.protocol + '//' + window.location.hostname;
    $scope.isMac = navigator.platform.slice(0,3) === "Mac";

    // shows/hides the sharing link UI, including the spinner
    $scope.toggleLink = function(object) {
        object.showingLinks = !object.showingLinks;
    };

    // Called when the user clicks the "Link" action
    // Or, if there's already a link, when they click the link icon
    // Generates a link, then...
    // Lets user see link URL and specify link permissions
    $scope.showLink = function(object) {
        if ($scope.enableLinksharing) {
            // if there is no link yet, create one
            if (object.links === undefined) {
                object.links = [];
            }
            if (object.links.length === 0) {
                $scope.createLink(object);
            } else {
                // otherwise, just show the link(s)
                $scope.toggleLink(object);
            }
        } else {
            showErrorMessage("Link-based sharing has been turned off for your organization.");
        }
    };

    $scope.createLink = function(object) {
        if (!object.showingLinks) {
            $scope.toggleLink(object);
        }
        $log.info("Creating link for object " + object.name);
        $http.post('/create_url', {
            soid: object.id
        }).success(function(response) {
            var newLink = {
                key: response.key,
                has_password: false,
                // all new links have no expiration, can be added later
                expires: 0
            };
            object.links.push(newLink);
        }).error(function(response, status) {
            // link creation request failed
            if (status == 400 && response.type == "NO_PERM") {
                $log.error("Unauthorized attempt to make link.");
                showErrorMessage("You are not authorized to make a link for this " +
                     "file or folder. " +
                     "You must be an owner of the folder in order to share it.");
            } else {
                $log.debug("Link creation failed with status " + status);
                showErrorMessageUnsafe(getInternalErrorText());
            }
            if (object.links.length === 0) {
                // hide spinner
                $scope.toggleLink(object);
            }
        });
    };

    // Toggles whether the link is in copyable mode or not
    // (meaning replacing it w. a text input and a helpful note)
    $scope.toggleCopying = function(link) {
        if (link.copying) {
            link.copying = false;
        } else {
            link.copying = true;
        }
    };
}]);


shelobControllers.controller('LoginCtrl',
    ['$scope', '$rootScope',
    function ($scope, $rootScope) {
        $scope.loginFunction = function(password) {
            /* Hack: put password in root scope, so other controller can access it */
            $rootScope.linkPasswordEntered = password;
            password = '';
            // redirect to main page
            window.location.hash = '#/';
        };
    }]);
