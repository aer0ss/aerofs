var shelobApp = angular.module('shelobApp', [
    'ngRoute',
    'ui.bootstrap',
    'treeControl',
    'angular.filter',
    'typeahead',
    'shelobControllers',
    'shelobServices',
    'shelobDirectives',
    'shelobFilters',
    'shadowfaxDirectives',
    'shadowfaxFilters'
]);

shelobApp.config(['$routeProvider', '$locationProvider', function($routeProvider, $locationProvider) {
    $routeProvider
        .when('/authorize', {
            templateUrl: '/static/shelob/partials/link-authorization.html',
            controller: 'LinkAuthorizationCtrl'
        })
        .when('/:oid', {
            templateUrl: '/static/shelob/partials/file-list.html',
            controller: 'FileListCtrl'
        })
        .when('/', {
            templateUrl: '/static/shelob/partials/file-list.html',
            controller: 'FileListCtrl'
        })
        .otherwise({
            redirectTo: '/root'
        });

    //To be used with setting $location.path in the controller
    //This prevents a page reload when navigating to a hashed route.
    $locationProvider.html5Mode(false);
    $locationProvider.hashPrefix('');
}]);
