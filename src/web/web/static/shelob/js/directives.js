var shelobDirectives = angular.module('shelobDirectives', []);

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
                $timeout(function() { elem[0].focus(); });
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
}
