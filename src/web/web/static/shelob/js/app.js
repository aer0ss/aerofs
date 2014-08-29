var shelobApp = angular.module('shelobApp', [
    'ngRoute',
    'ui.bootstrap',
    'treeControl',
    'shelobControllers',
    'shelobServices',
    'shelobDirectives',
    'shelobFilters'
]);

shelobApp.config(['$routeProvider', function($routeProvider) {
    $routeProvider.
        when('/login', {
            templateUrl: '/static/shelob/partials/login.html',
            controller: 'LoginCtrl'
        }).when('/:oid', {
            templateUrl: '/static/shelob/partials/file-list.html',
            controller: 'FileListCtrl'
        }).when('/', {
            templateUrl: '/static/shelob/partials/file-list.html',
            controller: 'FileListCtrl'
        }).
        otherwise({
            redirectTo: '/root'
        });
}]);
