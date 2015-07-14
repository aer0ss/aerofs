var sarumanFilters = angular.module('sarumanFilters', []);

sarumanFilters.filter('capitalize', [function(){
    return function(input) {
        if (input) {
            input = input.toLowerCase();
            return input.substring(0,1).toUpperCase() + input.substring(1);
        }
    };
}]);