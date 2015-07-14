var fellowshipFilters = angular.module('fellowshipFilters', []);

fellowshipFilters.filter('memberFilter', [function(){
    return function(input) {
        var s = '';
        if (input.length < 1) {
            return '';
        } else {
            // start off string with first email
            s = input[0].email;
            // show max 3 emails
            var limit = 3;
            // drop limit if only two emails
            if (input.length < limit) {
                limit = input.length;
            }
            // add additional emails to string
            for (var i = 1; i < limit; i++) {
                s = s + ', ' + input[i].email;
            }
        }
        return s;
    };
}]);