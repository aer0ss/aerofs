var sarumanDirectives = angular.module('sarumanDirectives', []);

sarumanDirectives.directive('ngDeviceRow', function() {
    return {
        restrict: 'A',
        scope: false,
        templateUrl: '/static/saruman/partials/device-row.html'
    };
});