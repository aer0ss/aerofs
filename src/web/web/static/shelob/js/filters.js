var shelobFilters = angular.module('shelobFilters', []);

shelobFilters.filter('humanBytes', function() {
    var rounded = function(n, divisor) {
        return Math.round(n * 10 / divisor) / 10;
    };
    return function(input) {
        if (input >= 0) {
            if (input < 1024) {
                return input.toString() + ' B';
            } else if (input < 1048576) {
                return rounded(input, 1024).toString() + ' KB';
            } else if (input < 1073741824) {
                return rounded(input, 1048576).toString() + ' MB';
            } else {
                return rounded(input, 1073741824).toString() + ' GB';
            }
        } else {
            return '--';
        }
    };
});
