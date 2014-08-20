var fellowshipDirectives = angular.module('fellowshipDirectives', []);

fellowshipDirectives.directive('aeroGroupRow', function() {
    return {
        restrict: 'A',
        scope: false,
        templateUrl: '/static/fellowship/partials/group-row.html'
    };
});

fellowshipDirectives.directive('aeroGroupname', ['$rootScope', function($rootScope){
    return {
        require: 'ngModel',
        link: function(scope, elm, attrs, ctrl) {
          ctrl.$parsers.unshift(function(viewValue) {
            if ($rootScope.knownGroupNames.indexOf(viewValue) === -1) {
              // it is valid
              ctrl.$setValidity('groupname', true);
              return viewValue;
            } else {
              // it is invalid, return undefined (no model update)
              ctrl.$setValidity('groupname', false);
              return undefined;
            }
          });
        }
    };
}]);