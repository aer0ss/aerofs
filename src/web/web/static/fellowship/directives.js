var fellowshipDirectives = angular.module('fellowshipDirectives', []);

fellowshipDirectives.directive('aeroGroupRow', function() {
    return {
        restrict: 'A',
        scope: false,
        templateUrl: '/static/fellowship/partials/group-row.html'
    };
});

fellowshipDirectives.directive('aeroGroupname', ['$http', '$log', function($http, $log){
    return {
        restrict: 'A',
        scope: {
            isInvalid: '=',
            newGroup: '=ngModel'
        },
        template:   '<input type="text" id="name" name="name" class="form-control"' +
                    'ng-model="newGroup.name">' +
                    '<span class="help-block" ng-show="isInvalid"><span class="text-danger">' +
                    'This group name is unavailable.</span></span>',
        link: function(scope, elm, attrs, ctrl) {
            scope.knownGroupNames = [];
            scope.isInvalid = false;
            scope.oldGroup = angular.copy(scope.newGroup);
            console.log(scope.newGroup.name);

            var isUniqueName = function(name){
                $http.get(getGroupsURL, {
                    params: {
                        substring: name
                    }
                }).success(function(response){
                    for (var i = 0; i < response.groups.length; i++) {
                        if (response.groups[i].name == name) {
                            scope.isInvalid = true;
                            scope.knownGroupNames.push(name);
                            return;
                        }
                    }
                    scope.isInvalid = false;
                }).error(function(response, status){
                    $log.error('Failed to check group name for uniqueness.');
                    scope.isInvalid = false;
                });
            };
            scope.$watch('newGroup.name', function(newValue, oldValue){
                if (newValue != scope.oldGroup.name) {
                    if (scope.knownGroupNames.indexOf(newValue) === -1) {
                        isUniqueName(newValue);
                    } else {
                        scope.isInvalid = true;
                    }
                } else {
                    scope.isInvalid = false;
                }
            });
        }
    };
}]);