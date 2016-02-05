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
