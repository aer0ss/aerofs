var sarumanControllers = angular.module('sarumanControllers', []);

sarumanControllers.controller('DevicesController', ['$scope', '$rootScope', '$log', '$modal', '$http',
    function($scope, $rootScope, $log, $modal, $http){
        var csrftoken = $('meta[name=csrf-token]').attr('content');
        $http.defaults.headers.common["X-CSRF-Token"] = csrftoken;

        $http.get('/devices/get_devices', {
            params: {
                user: $("#username")[0].innerHTML
            }
        }).success(function(data){
            $scope.devices = data.devices.concat(data.mobile_devices);
            $rootScope.devices = $scope.devices;
        }).error(function(response){
            $log.warn('Device data failed to load.');
            showErrorMessageFromResponse(response);
        });

        $scope.changeName = function(device) {
            if (!device.newName) {
                device.changingName = false;
                return;
            }
            var oldName = device.name;
            device.name = device.newName;
            $http.post(deviceRenameURL, {
                device_id: device.id,
                device_name: device.name
            }).success(function(){
                $log.info("Device name changed to " + device.name + ".");
                device.changingName = false;
            }).error(function(response){
                $log.warn('Device rename failed.');
                device.name = oldName;
                showErrorMessageFromResponse(response);
            });
        };

        $scope.unlink = function(device){
            unlinkOrErase(device, 'unlink');
        };
        $scope.erase = function(device) {
            unlinkOrErase(device, 'erase');
        };

        var unlinkOrErase = function(device, action) {
            var unlinkEraseModalCtrl = function ($scope, $rootScope, $modalInstance, device, action) {
                $scope.device = device;
                $scope.action = action;

                $scope.ok = function () {
                    var url;
                    if ($scope.device.is_mobile) {
                        url = revokeTokenURL;
                        data = {
                            access_token: device.token
                        };
                    } else {
                        data = {
                            device_id: $scope.device.id
                        };
                        if (action == "erase"){
                            url = eraseURL;
                        } else if (action == "unlink"){
                            url = unlinkURL;
                        } else {
                            $log.info("Invalid action '" + action + "' provided.");
                            return;
                        }
                    }

                    $http.post(url, data).success(function(){
                        if (action == "erase") {
                            $log.info("Device has been erased.");
                        } else {
                            $log.info("Device has been unlinked.");
                        }
                        for (var i=0; i < $rootScope.devices.length; i++) {
                            if ($rootScope.devices[i] == $scope.device) {
                                $rootScope.devices.splice(i,1);
                            }
                        }
                        $modalInstance.close();
                    }).error(function(response){
                        if (action == "erase") {
                            $log.warn("Device erasure failed.");
                        } else {
                            $log.warn("Device unlinking failed.");
                        }
                        showErrorMessageFromResponse(response);
                        $modalInstance.close();
                    });
                };
                $scope.cancel = function () {
                    $modalInstance.dismiss('cancel');
                };
            };

            var modalInstance = $modal.open({
                templateUrl: '/static/saruman/partials/unlink-erase-warning.html',
                controller: unlinkEraseModalCtrl,
                resolve: {
                    device: function() {
                        return device;
                    },
                    action: function(){
                        return action;
                    }
                }
            });
        };
    }]);
