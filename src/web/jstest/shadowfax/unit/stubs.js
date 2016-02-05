'use-strict';
//For strider

//Variables from inline script in shared_folders.mako
var dataUrl = '/get_org_shared_folders';
var getUsersAndGroupsURL = '/users_and_groups/list';
var canAdminister = false;
var isAdmin = false;
var paginationLimit = 20;
var hasPagination = true;