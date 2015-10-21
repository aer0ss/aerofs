var shelobDirectives = angular.module('shelobDirectives', ['shelobConfig']);

shelobDirectives.directive('aeroFileUpload', function($rootScope, $routeParams, $log, $modal, $timeout, API, IS_PRIVATE) {  return {

    restrict: 'EA',

    templateUrl: '/static/shelob/partials/file-upload.html',

    replace: true,

    link: function(scope, elem, attrs) {

        scope.doChunkedUpload = function(fileJSON, file, etag) {
            var headers = {'If-Match': etag};
            //TODO: use page size (4M or 8M) chunks, and increase nginx's max body size
            var chunkSize = 1024 * 1024;
            API.chunkedUpload(fileJSON.id, file, chunkSize, headers).then(function(response) {
                // file upload succeeded
                $log.info('file upload succeeded');
                $timeout(function() {
                    scope.progressModal.close();
                    showSuccessMessage('Successfully uploaded "' + fileJSON.name + '"');
                    fileJSON.type = 'file';
                    scope.objects.push(fileJSON);
                }, 500);
            }, function(response) {
                // file upload failed
                scope.progressModal.dismiss();
                if (response.reason == 'read') {
                    showErrorMessage("Upload failed: could not read file from disk.");
                } else if (response.reason == 'upload') {
                    if (response.status == 503) showErrorMessageUnsafe(getClientsOfflineErrorText());
                    else if (response.status == 409) showErrorMessage("A file or folder with that name already exists.");
                    else if (response.status == 403) showErrorMessage("Only users with Owner or Editor permissions can upload a file to this shared folder. Contact an Owner of this shared folder to get permissions.");
                    else showErrorMessageUnsafe(getInternalErrorText());
                } else showErrorMessageUnsafe(getInternalErrorText());
            }, function(response) {
                // file upload notification
                $log.debug('progress report:', response.progress);
                $rootScope.$broadcast('modal.progress', {progress: response.progress});
            });
        };

        scope.upload = function() {
            if (!window.File || !window.FileReader) {
                showErrorMessage("File uploads are not supported in this browser.");
                return;
            }

            // get file object from <input>
            var file = elem[0].children[1].files[0];
            if (file === undefined) return;

            // open progress modal
            scope.progressModal = $modal.open({
                templateUrl: '/static/shelob/partials/file-download-modal.html',
                // don't close modal when clicking the backdrop or hitting escape
                backdrop: 'static',
                keyboard: false,
                controller: function($scope) {
                    $scope.$on('modal.progress', function(event, args) {
                        $scope.progress = Math.round(100 * args.progress);
                    });
                }
            });

            // create an empty file before upload
            scope.rootFolder = $routeParams.oid;
            if (!$routeParams.oid) {
                scope.rootFolder = 'root';
            }
            var fileObj = {parent: scope.rootFolder, name: file.name};
            $log.debug("creating file:", fileObj);
            var headers = {'Endpoint-Consistency': 'strict'};
            API.post('/files', fileObj, headers).then(function(response) {
                // file creation succeeded
                $log.info('file creation succeeded');
                scope.doChunkedUpload(response.data, file, response.data.etag);
            }, function(response) {
                // file creation failed
                $log.error('file creation failed with status ' + response.status);
                scope.progressModal.dismiss();
                if (response.status == 503) showErrorMessageUnsafe(getClientsOfflineErrorText());
                else if (response.status == 409) showErrorMessage("A file or folder with that name already exists.");
                else if (response.status == 403) showErrorMessage("Only users with Owner or Editor permissions can upload a file to this shared folder. Contact an Owner of this shared folder to get permissions.");
                else showErrorMessageUnsafe(getInternalErrorText());
            });
        };
    }
};
});

shelobDirectives.directive('aeroInPlaceEdit', function($timeout) { return {
    restrict: 'EA',

    scope: {
        focusOn: '=',
        onSubmit: '&',
        onCancel: '&',
    },

    link: function(scope, elem, attrs) {
        // autofocus on input when focus-on attr changes to true
        scope.$watch('focusOn', function() {
            if (scope.focusOn) {
                // Setting element.focus() directly does not work, because
                // the element has not yet been transformed by the directive.
                // Setting it in a timeout waits until the "behind the scenes"
                // directive magic is done so that the element exists when you
                // try to focus on it. See:
                // http://lorenzmerdian.blogspot.com/2013/03/how-to-handle-dom-updates-in-angularjs.html
                $timeout(function() {
                    elem[0].focus();
                    elem[0].select();
                });
            }
        });

        // sigh, bind to "keyup" because "keydown" doesn't work in Firefox and
        // "keypress" doesn't work in Chrome
        elem.bind("keyup", function(event) {
            if (event.which === 13) {
                // handle Return keypress
                scope.onSubmit();
            } else if (event.which === 27) {
                // handle Escape keypress
                scope.onCancel();
            }
        });

        // bind the "blur" event to the onSubmit function so that clicking away
        // from the input submits the new text
        elem.bind("blur", function(event) {
            scope.onSubmit();
        });
    }
};
});


shelobDirectives.directive('aeroIcon', function() { return {
    restrict: 'EA',

    template: '<img ng-src="/static/shelob/img/icons/40x40/{{filename}}"/>',

    replace: true,

    scope: {
        mimeType: '@',
    },

    link: function(scope, elem, attrs) {
        scope.filename = getFilename(scope.mimeType);
    }
};
});


// This function maintains a mapping of mimetypes to icon file names.
// A similar mapping exists in the Android app. Make sure they stay in sync.
getFilename = function(mimeType) {
    if (mimeType.indexOf('text/', 0) === 0)
    {
        return 'filetype_text.png';
    }
    else if (mimeType.indexOf('image/', 0) === 0)
    {
        return 'filetype_image.png';
    }
    else if (mimeType.indexOf('audio/', 0) === 0)
    {
        return 'filetype_audio.png';
    }
    else if (mimeType.indexOf('video/', 0) === 0)
    {
        return 'filetype_video.png';
    }
    else if (mimeType == 'application/msword' ||
             mimeType == 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' ||
             mimeType == 'application/vnd.openxmlformats-officedocument.wordprocessingml.template' ||
             mimeType == 'application/vnd.ms-word.document.macroEnabled.12' ||
             mimeType == 'application/vnd.ms-word.template.macroEnabled.12'
    ){
        return 'filetype_word.png';
    }
    else if (mimeType == 'application/vnd.ms-excel' ||
             mimeType == 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' ||
             mimeType == 'application/vnd.openxmlformats-officedocument.spreadsheetml.template' ||
             mimeType == 'application/vnd.ms-excel.sheet.macroEnabled.12' ||
             mimeType == 'application/vnd.ms-excel.template.macroEnabled.12' ||
             mimeType == 'application/vnd.ms-excel.addin.macroEnabled.12' ||
             mimeType == 'application/vnd.ms-excel.sheet.binary.macroEnabled.12'
    ){
        return 'filetype_excel.png';
    }
    else if (mimeType == 'application/vnd.ms-powerpoint' ||
             mimeType == 'application/vnd.openxmlformats-officedocument.presentationml.presentation' ||
             mimeType == 'application/vnd.openxmlformats-officedocument.presentationml.template' ||
             mimeType == 'application/vnd.openxmlformats-officedocument.presentationml.slideshow' ||
             mimeType == 'application/vnd.ms-powerpoint.addin.macroEnabled.12' ||
             mimeType == 'application/vnd.ms-powerpoint.presentation.macroEnabled.12' ||
             mimeType == 'application/vnd.ms-powerpoint.template.macroEnabled.12' ||
             mimeType == 'application/vnd.ms-powerpoint.slideshow.macroEnabled.12'
    ){
        return 'filetype_powerpoint.png';
    }
    else if (mimeType == 'application/pdf')
    {
        return 'filetype_pdf.png';
    }
    else if (mimeType == 'application/x-tar' ||
             mimeType == 'application/zip'
    ){
        return 'filetype_compressed.png';
    }
    else {
        return 'filetype_generic.png';
    }
};

// Generates a file/folder row for an object.
// Uses different templates to render with depending on whether this is 
// a My Files admin page or a linkshare page
shelobDirectives.directive('aeroFileRow', function() {
    return {
        restrict: 'A',
        scope: false,
        templateUrl: function(tElement, tAttrs) {
            /* Is this a link sharing page?
            Note: if linkshare page path changes from /l/<whatever>, this will break.
            See url_sharing_view.py for more details! */
            var pathList = window.location.pathname.split('/');
            if (pathList.length > 1 && pathList[1] === "l") {
                template = 'link-share';
            } else {
                template = 'my-files';
            }
            return '/static/shelob/partials/file-row-' + template + '.html';
        }
    };
});

shelobDirectives.directive('aeroLinkOptions', ['$modal','$log','$http','$timeout', function($modal, $log, $http, $timeout){
    return {
        restrict: 'A',
        require: '^ngModel',
        scope: {
          link: '=ngModel',
          title: '@',
          deletionCallback: '&onDelete'
        },
        templateUrl: '/static/shelob/partials/link-options.html',
        link: function($scope, element, attrs) {

            //Sets the current link to require login
            $scope.setRequireLogin = function () {

                $http.post('/set_url_require_login', {
                    key: $scope.link.key,
                    require_login: !$scope.link.require_login
                }).success(function(response){
                    if ($scope.link.require_login) {
                        //It used to require a login, now it doesn't.
                        showSuccessMessage("Anyone with the link can access it.");
                        $scope.link.require_login = false;
                    } else {
                        showSuccessMessage("Only signed-in users can access the link.");
                        $scope.link.require_login = true;
                    }
                }).error(function () {
                    showErrorMessageUnsafe(getInternalErrorText());
                });
            },

            // Deletes shareable link
            $scope.deleteLink = function() {
                // destroy link on server
                $http.post('/remove_url', {
                    key: $scope.link.key
                }).success(function(response){
                    showSuccessMessage("You have deleted the link " + $scope.link.key);
                    if ($scope.deletionCallback && $scope.$parent.object) {
                        // remove link from list of links, if relevant
                        $scope.deletionCallback({
                            object: $scope.$parent.object,
                            key: $scope.link.key
                        });
                    } else {
                        // blank out linkshare page
                        $scope.link = undefined;
                    }
                }).error(function(response){
                    showErrorMessageUnsafe(getInternalErrorText());
                });
            };

            $scope.removePassword = function(){
                $http.post('/remove_url_password', {
                    key: $scope.link.key
                }).success(function(response){
                    $scope.link.has_password = false;
                    delete $scope.link.password;
                    $log.info('You have removed the password on link ' + $scope.link.key + '.');
                }).error(function(response){
                    showErrorMessageUnsafe(getInternalErrorText());
                });
            };

            // function that produces list of values for expiration dates, in seconds
            var _expiration_options = function(newOption){
                var options = [0,3600,86400,604800,-1];
                if (newOption && options.indexOf(newOption) === -1) {
                    var custom = options.pop();
                    options.push(newOption);
                    options.push(custom);
                }
                return options;
            };

            $scope.password = function() {
                var PasswordCtrl = function ($scope, $modalInstance, link) {
                    $scope.link = link;
                    // on modal open, make temp version of link
                    $scope.linkOld = angular.copy($scope.link);

                    $scope.cancel = function () {
                        $scope.link = $scope.linkOld;
                        $modalInstance.close();
                    };

                    $scope.ok = function () {
                        if ($scope.link.password){
                            $http.post('/set_url_password', {
                                key: $scope.link.key,
                                password: $scope.link.password
                            }).success(function(response){
                                $scope.link.has_password = true;
                                $log.info('You have changed the password on link ' + $scope.link.key + '.');
                                $modalInstance.close();
                            }).error(function(response){
                                showErrorMessageUnsafe(getInternalErrorText());
                                $modalInstance.close();
                            });
                        } else {
                            $scope.cancel();
                        }
                    };
                };

                var modalInstance = $modal.open({
                    templateUrl: '/static/shelob/partials/password-modal.html',
                    controller: PasswordCtrl,
                    resolve: {
                        link: function() {
                            return $scope.link;
                        }
                    }
                });
            };

            $scope.expiry = function() {
                var ExpiryCtrl = function ($scope, $modalInstance, link) {
                    $scope.link = link;
                    $scope.link.expiration_options = _expiration_options($scope.link.expires);
                    // on modal open, make temp version of link
                    $scope.linkOld = angular.copy($scope.link);

                    $scope.cancel = function () {
                        $scope.link.expires = $scope.linkOld.expires;
                        $modalInstance.close();
                    };

                    $scope.ok = function () {
                        if ($scope.link.expires < 0) {
                            var data = $($('#expiry-modal .datepicker input')[0]).val().split('-');
                            var date = new Date(parseInt(data[0],10), parseInt(data[1],10)-1, parseInt(data[2],10),23,59,59);
                            var elapsed = Math.floor((date - Date.now())/1000) - date.getTimezoneOffset() * 60;
                            $scope.link.expiration_options = _expiration_options(elapsed);
                            $scope.link.expires = elapsed;
                        }
                        $http.post('/set_url_expires', {
                                key: link.key,
                                expires: link.expires
                            }).success(function(response){
                                $log.info('You have modified the expiration settings of link "'+ $scope.link.key +'".');
                                $modalInstance.close();
                            }).error(function(response){
                                showErrorMessageUnsafe(getInternalErrorText());
                                $modalInstance.close();
                            });
                  };
                };

                var modalInstance = $modal.open({
                    templateUrl: '/static/shelob/partials/expiration-modal.html',
                    controller: ExpiryCtrl,
                    resolve: {
                        link: function() {
                            return $scope.link;
                        }
                    }
                });
                modalInstance.opened.finally(function() {
                    // because it's a transclude, .opened promise doesn't actually
                    // mean the content's in the DOM yet >:(
                    // wrapping with $timeout service hacks around this
                    $timeout(function(){
                        // initialize datepicker
                        var d = new Date();
                        var curr_day = d.getDate();
                        var curr_month = d.getMonth() + 1; // Months are zero based
                        if (curr_month < 10) {
                            curr_month = '0' + curr_month;
                        }
                        var curr_year = d.getFullYear();
                        var format = curr_year + '-' + curr_month + "-" + curr_day;
                        $("#expiry-modal .datepicker input").attr('value', format);

                        // calling the datepicker for bootstrap plugin
                        // https://github.com/eternicode/bootstrap-datepicker
                        // http://eternicode.github.io/bootstrap-datepicker/
                        $('.datepicker').datepicker({
                            autoclose: true,
                            startDate: new Date()
                        });
                    }, 0);
                });
            };
        }
    };
}]);
