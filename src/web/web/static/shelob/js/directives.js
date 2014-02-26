var shelobDirectives = angular.module('shelobDirectives', []);

shelobDirectives.directive('aeroIcon', function() { return {
    restrict: 'EA',

    template: '<img ng-src="/static/shelob/img/icons/40x40/{{filename}}"/>',

    link: function(scope, elem, attrs) {
        scope.$watch(attrs.mimeType, function(value) {
            var mimeType = attrs.mimeType;
            // N.B. this function could be run before the DOM is initialized, so we perform
            // the following checks to avoid unpredictable behaviour
            if ((value !== null) && (value !== undefined) && (value !== '') &&
                    (mimeType !== null) && (mimeType !== undefined) && (mimeType !== '')) {

                scope.filename = getFilename(mimeType);
            }
        });
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
}
