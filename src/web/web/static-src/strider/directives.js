var striderDirectives = angular.module('striderDirectives', []);

striderDirectives.directive('aeroUserRow', function() {
    return {
        restrict: 'A',
        scope: false,
        templateUrl: '/static/strider/partials/user-row.html'
    };
});

striderDirectives.directive('aeroInviteeRow', function() {
    return {
        restrict: 'A',
        scope: false,
        templateUrl: '/static/strider/partials/invitee-row.html'
    };
});