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

// Helper for humanTime() and humanDate()
var _pluralize = function(n) {
    return n === 1 ? '' : 's';
};

// Takes a number of seconds, returns a string for how much time remains
shelobFilters.filter('humanTime', function() {
    return function(seconds) {
        var minutes = Math.floor(seconds/60);
        if (seconds < 0) {
            return "Custom...";
        }
        else if (minutes === 0) {
            return "Never";
        } else if (minutes < 60) {
            return minutes.toString() + " minute" + _pluralize(minutes);
        } else if (minutes === 60){
            return "1 hour";
        } else if (minutes < 1440) {
            return Math.floor(minutes/60).toString() + " hour" + _pluralize(minutes/60);
        } else if (minutes === 10080) {
            return "1 week";
        } else if (minutes < 43200) {
            return Math.floor(minutes/1440).toString() + " day" + _pluralize(minutes/1440);
        } else {
            return Math.floor(minutes/43200).toString() + " month" + _pluralize(minutes/43200);
        }
    };
});

// Takes a number of seconds, returns a date string for the remaining time
shelobFilters.filter('humanDate', function() {
    return function(seconds) {
        var minutes = Math.floor(seconds/60);
        if (seconds <= 0) {
            return "Never";
        } else if (minutes <= 60) {
            return minutes.toString() + " minute" + _pluralize(minutes);
        } else if (minutes < 1440) {
            return Math.floor(minutes/60).toString() + " hour" + _pluralize(minutes/60) + ', ' +
                (minutes % 60).toString() + ' minute' + _pluralize(minutes % 60);
        } else if (minutes >= 1440) {
            var expirationDate = new Date(Date.now() + minutes*60000);
            return expirationDate.toString().split(' ').slice(0,4).join(' ');
        }
    };
});