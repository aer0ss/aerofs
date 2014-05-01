var shelobDirectives = angular.module('shelobDirectives', []);

shelobDirectives.directive('aeroFileUpload', function($rootScope, $routeParams, $log, $modal, $timeout, API) {  return {

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
                    $rootScope.objects.push(fileJSON);
                }, 500);
            }, function(response) {
                // file upload failed
                scope.progressModal.dismiss();
                if (response.reason == 'read') {
                    showErrorMessage("Upload failed: could not read file from disk.");
                } else if (response.reason == 'upload') {
                    if (response.status == 503) showErrorMessage(getClientsOfflineErrorText());
                    else if (response.status == 409) showErrorMessage("A file or folder with that name already exists.");
                    else showErrorMessage(getInternalErrorText());
                } else showErrorMessage(getInternalErrorText());
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
            var fileObj = {parent: $routeParams.oid, name: file.name};
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
                if (response.status == 503) showErrorMessage(getClientsOfflineErrorText());
                else if (response.status == 409) showErrorMessage("A file or folder with that name already exists.");
                else showErrorMessage(getInternalErrorText());
            });
        };
    },
}});

shelobDirectives.directive('inPlaceEdit', function($timeout) { return {
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
    },
}});


shelobDirectives.directive('aeroIcon', function() { return {
    restrict: 'EA',

    template: '<img ng-src="/static/shelob/img/icons/40x40/{{filename}}"/>',

    replace: true,

    scope: {
        mimeType: '@',
    },

    link: function(scope, elem, attrs) {
        scope.filename = getFilename(scope.mimeType);
    },
}});


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
