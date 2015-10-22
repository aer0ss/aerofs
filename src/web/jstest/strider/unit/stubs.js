'use-strict';
//For strider
angular.module('ngSanitize', []);

//Variables from inline script in org_users.mako
var userFoldersURL = '/users/shared_folders';
var userDevicesURL = '/users/devices';
var userDataURL = '/users/list';
var isAdmin = false;
var paginationLimit = 20;