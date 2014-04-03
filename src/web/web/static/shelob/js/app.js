var shelobApp = angular.module('shelobApp', [
    'ngRoute',
    'ui.bootstrap',
    'treeControl',
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
        otherwise({
            redirectTo: '/root'
        });
}]);
