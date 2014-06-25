var shelobFilters = angular.module('shelobFilters', []).filter('humanBytes', function(){
    var rounded = function(n) {
        return Math.round(n*10/1024)/10;
    };
    return function(input) {
        if (input > 0) {
            if (input < 1024) {
                return input.toString() + ' B';
            } else if (input < 1048576) {
                return rounded(input).toString() + ' KB';
            } else if (input < 1073741824) {
                return rounded(input).toString() + ' MB';
            } else {
                return rounded(input).toString() + ' GB';
            }
        } else {
            return '--';
        }
    };
});