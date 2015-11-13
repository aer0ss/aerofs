var pagination = angular.module('pagination', ['ui.bootstrap']);

pagination.directive('aeroPagination', function () {
    return {
        restrict: 'A',
        scope: {
            total: '=',
            offset: '=',
            pagelimit: '=',
            callback: '&'
        },
        templateUrl: '/static/ng-modules/pagination/pagination.html',
        link: function ($scope, element, attrs) {

            $scope.maxSize = 5;
            $scope.currentPage = 1;
            $scope.updatePages = function () {
                $scope.pages = [];
                $scope.currentPage = 1;
                // do we need pagination? if so, let's count up pages
                if ($scope.total > $scope.pagelimit) {
                    for (var j=1; j < Math.ceil($scope.total / $scope.pagelimit) + 1; j++) {
                        $scope.pages.push(j);
                    }
                }
            };
            $scope.loadNewPage = function (newPage, oldPage) {
                if (newPage != oldPage) {
                    $scope.callback({offset: ($scope.currentPage - 1) * $scope.pagelimit});
                }
            };

            $scope.$watch('total', $scope.updatePages);

            $scope.$watch('currentPage',$scope.loadNewPage);

        }
    };
});
