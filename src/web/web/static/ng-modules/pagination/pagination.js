var pagination = angular.module('pagination', []);

var getModuleBaseURL = function(scriptName) {
    currentScriptPath = document.querySelector("script[src$='" + scriptName + "']").src;
    return currentScriptPath.substring(0, currentScriptPath.lastIndexOf('/') + 1);
};

pagination.directive('aeroPagination', function() {
    return {
        restrict: 'A',
        scope: {
            total: '=',
            offset: '=',
            pagelimit: '=',
            callback: '&'
        },
        templateUrl: '/static/ng-modules/pagination/pagination.html',
        link: function($scope, element, attrs) {
            $scope.$watch('total', function(newValue, oldValue){
                $scope.pages = [];
                // do we need pagination? if so, let's count up pages
                if ($scope.total > $scope.pagelimit) {
                    for (var j=1; j < Math.ceil($scope.total / $scope.pagelimit) + 1; j++) {
                        $scope.pages.push(j);
                    }
                }
            });

            $scope.getCurrentPage = function() {
                // FYI: page num counts up from 1
                return Math.ceil($scope.offset/$scope.pagelimit) + 1;
            };

            $scope.showPage = function(pageNum) {
                // FYI: page num counts up from 1
                if (pageNum > 0) {
                    // change offset to where the target page will start
                    $scope.offset = (pageNum - 1) * $scope.pagelimit;
                    $scope.callback({offset: $scope.offset });
                }
            };
        }
    };
});
