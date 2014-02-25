var shelobControllers = angular.module('shelobControllers', []);

shelobControllers.controller('FileListCtrl', ['$rootScope', '$http', '$log', '$routeParams', '$window', 'FileList', 'Token',
        function ($scope, $http, $log, $routeParams, $window, FileList, Token) {

    var oid = typeof $routeParams.oid === "undefined" ? '' : $routeParams.oid;

    FileList.get(oid).then(function(data) {
        // success callback
        $scope.files = data.files;
        $scope.folders = data.folders;
    }, function(status) {
        // failure callback
        $log.error('list children call failed with ' + status);
        if (status == 503) {
            showErrorMessage(getClientsOfflineErrorText());
        } else {
            showErrorMessage(getInternalErrorText());
        }
    });

    // This is called when a user clicks on a link to a file
    //
    // It should direct the browser to download the file if possible, or show
    // an appropriate error message.
    //
    // Arguments:
    //   oid: the oid of the file to download
    //
    $scope.download = function(oid) {
        // TODO: use Token.get() and verify the token so that we don't send the user to a
        // 401 page if the token's expired. Until we do this, we play safe and get a brand
        // new token.
        Token.getNew().then(function(token) {
            // N.B replace(url) will replace the current history with url, whereas
            // assign(url) will append url to the history chain. We use replace() so
            // that a user can navigate from folder Foo to folder Bar, click to download
            // a file, and then use the back button to return to Foo.
            $window.location.replace("/api/v1.0/files/" + oid + "/content?token=" + token);
        }, function(status) {
            // called when getting a new token fails
            showErrorMessage(getInternalErrorText());
        });
    }
}]);
