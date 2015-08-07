var pagination = angular.module('pagination', []);

pagination.directive('ngPagination', function() {
    return {
        restrict: 'A',
        scope: false,
        templateUrl: '/static/shadowfax/partials/pagination.html'
    };
});

//
// TODO: figure out how modules are actually supposed to inject stuff like this
//
// to use: call pagination.activate() with the controller's scope,
// the number of items that go on a page, and whatever data-fetching callback function you have.
// Your callback function will need to use $scope.offset
// and set $scope.total based on reply.
pagination.activate = function ($scope, overflow, pageChangeCallback) {
    $scope.offset = 0;
    // total value will be overwritten later by server response
    $scope.total = 0;

    $scope.calculatePages = function(available) {
        $scope.pages = [];
        // do we need pagination? if so, let's count up pages
        if ($scope.total > available) {
            for (var j=1; j < Math.ceil($scope.total / overflow) + 1; j++) {
                $scope.pages.push(j);
            }
        }
    };

    $scope.getCurrentPage = function() {
        return Math.ceil($scope.offset/overflow) + 1;
    };

    $scope.showPage = function(pageNum) {
        if (pageNum > 0) {
            // change offset to where the target page will start
            $scope.offset = (pageNum - 1) * overflow;
            // refresh page data
            pageChangeCallback();
        }
    };
    return $scope;
};
