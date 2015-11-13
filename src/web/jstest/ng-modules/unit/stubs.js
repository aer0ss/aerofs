'use-strict';

//Variables from inline script in org_users.mako
var userDataURL = '/users/list';
var updateUsers = function(users, total, substring) { return false; };
var restore = function () {return false};

//For pagination
var pages = [1,2,3];
var paginationInfo = {
    total: 45,
    offset: 0,
    limit: 20,
    callback: function (offset, substring) { return false; }
};