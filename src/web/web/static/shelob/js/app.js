var shelobApp = angular.module('shelobApp', [
    'ngRoute',
    'ui.bootstrap',
    'shelobControllers',
    'shelobServices',
    'shelobDirectives',
]);

shelobApp.config(['$routeProvider', function($routeProvider) {
    $routeProvider.
        when('/:oid', {
            templateUrl: '/static/shelob/partials/file-list.html',
            controller: 'FileListCtrl',
        }).
        when('/', {
            templateUrl: '/static/shelob/partials/file-list.html',
            controller: 'FileListCtrl',
        }).
        otherwise({
            redirectTo: '/'
        });
}]);
