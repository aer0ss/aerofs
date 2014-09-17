var sarumanDirectives = angular.module('sarumanDirectives', []);

sarumanDirectives.directive('aeroDeviceRow', function() {
    return {
        restrict: 'A',
        scope: false,
        templateUrl: '/static/saruman/partials/device-row.html'
    };
});