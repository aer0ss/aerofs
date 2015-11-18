var striderApp = angular.module('striderApp', [
        'ui.bootstrap',
        'pagination',
        'typeahead',
        'striderControllers',
        'striderDirectives',
        'shadowfaxFilters',
    ]).factory('sharedProperties', function () {
        var data = {
            usersView: true,
            userCount: 0,
            inviteesCount: 0
        };

        return data;

    });