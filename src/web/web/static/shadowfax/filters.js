var shadowfaxFilters = angular.module('shadowfaxFilters', []);

var get_first_name = function(user, rootscope) {
        if (user.email == rootscope.me) {
            return 'me';
        } else if (user.first_name) {
            return user.first_name;
        // a group
        } else if (user.name) {
            return user.name;
        // an invitee who hasn't signed up yet, or other
        } else {
            return "Anonymous";
        }
    };

var get_full_name = function(user) {
    return user.first_name + ' ' + user.last_name;
};

shadowfaxFilters.filter('myName', ['$rootScope', function($rootScope){
    return function(input) {
        var first = get_first_name(input, $rootScope);
        if (first == "me") {
            return first;
        // an invitee who hasn't signed up yet
        } else if (first == "Anonymous" && input.email) {
            return input.email;
        } else if (input.last_name) {
            return get_full_name(input);
        // no full name for some reason
        } else {
            return first;
        }
    };
}]);


shadowfaxFilters.filter('fullName', ['$rootScope', function($rootScope){
    return function(input) {
        return get_full_name(input);
    };
}]);


shadowfaxFilters.filter('byRole',[function(){
    return function(input, role) {
        if (role === "Owner") {
            return input.filter(function(item){
                return item.is_owner;
            });
        } else if (role === "Member") {
            return input.filter(function(item){
                return !item.is_owner;
            });
        } else {
            return input;
        }
    };
}]);

/*
    Produces the string describing the owners or members of a folder.
    Note: this assumes that if the current user is an owner/member, they
    appear at the end of the list.
*/
shadowfaxFilters.filter('myMembers', ['$rootScope', '$log', function($rootScope, $log){
    return function(input) {
        var pendingless_input = [];
        for (var i = 0; i < input.length; i++) {
            if (!input[i].is_pending) {
                pendingless_input.push(input[i]);
            }
        }
        input = pendingless_input;
        var str = '';
        if (input.length === 0) {
            str = "--";
        } else if (input.length === 1) {
            str = get_first_name(input[0], $rootScope);
        } else if (input.length < 5) {
            for (var i = 0; i < input.length; i++) {
                if (i === input.length - 1) {
                    str += " and ";
                } else if (i > 0) {
                    str += ", ";
                }
                str += get_first_name(input[i], $rootScope);
            }
        } else {
            var printed = 2;
            for (var i = 0; i < printed; i++) {
                str += get_first_name(input[i], $rootScope);
                str += ', ';
            }
            if (input[input.length-1].email == $rootScope.me.email) {
                str += 'me, ';
                printed += 1;
            }
            str += "and " + (input.length - printed).toString() + " others";
        }
        return str;
    };
}]);