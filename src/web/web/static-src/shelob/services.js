var shelobServices = angular.module('shelobServices', ['shelobConfig']);

// This service should be used to keep track of the number of outstanding AJAX
// calls so that a spinner can be shown when there are requests outstanding.
shelobServices.factory('OutstandingRequestsCounter', [
    function() {
        return {
            get: function() { return aero.client.pendingRequests; }
        };
    }
]);

// This service is used to cache a user entered password between controllers
// for password protected links
shelobServices.factory('LinkPassword', [
    function() {
        return {
            userInput: null,
            resetUserInput: function () {
                this.userInput = null;
            }
        };
    }
]);

shelobServices.factory('Token', ['$http', '$q', '$log',
    function($http, $q, $log) {

    var token = null;
    var outstandingNewTokenRequest = false;

    return {


    // Get a brand new OAuth token from the server
    //
    // Note that even if there is an outstanding request to /json_token, we
    // should make a new request, since the web backend could return a stale
    // token. However, if there is an outstanding request to /json_new_token,
    // we return a promise that will be resolved with the returned token
    getNew: function() {
        if (!outstandingNewTokenRequest) {
            token = $q.defer();
            outstandingNewTokenRequest = true;
            $http.post('/json_new_token')
                .success(function (data) {
                    token.resolve(data.token);
                })
                .error(function (data, status) {
                    token.reject({data: data, status: status});
                })
                ["finally"](function() {
                    outstandingNewTokenRequest = false;
                });
        }
        return $q.when(token.promise);
    }
    };
}]);

// API methods return an angular promise (http://docs.angularjs.org/api/ng/service/$q)
// The promise is an object with a then(success, failure, notify) function that takes
// three callbacks, to which an object containing relevant response data will be passed
//
// e.g. call API.get(url).then(
//              function(r) { // handle success using r.data or r.headers },
//              function(r) { // handle failure using r.data or r.status });
//
shelobServices.factory('API', ['$log', '$q', 'Token', 'API_LOCATION',
    function($log, $q, Token, API_LOCATION) {
        aero.initialize({
            hostName : API_LOCATION,
            apiVersion : "1.4",
            expireCb : Token.getNew,
            cache : false
        });
        $log.debug("Created AeroFS api config ", aero.config);

        // Browser ES6 promises must be wrapped in a $q promise
        // for two way binding to work. The functions of all exposed API
        // modules are wrapped with $q.when(...) to accomplish this. This
        // ensures view updates are triggered as the result of a promise

        var compose = function(a, b) {
           return function () {
              return a(b.apply(null, arguments));
          };
        };

        var modules = [ aero.api.user,
                        aero.api.sf,
                        aero.api.folder,
                        aero.api.file,
                        aero.api.sfmember,
                        aero.api.sfpendingmember,
                        aero.api.sfgroupmember ];
        modules.forEach( function(module) {
            for (var key in module) {
                var prop = module[key];
                if (prop instanceof Function) {
                    module[key] = compose($q.when, prop);
                }
            }
        });

        return {
            user : aero.api.user,
            sf : aero.api.sf,
            sfmember : aero.api.sfmember,
            sfpendingmember : aero.api.sfpendingmember,
            sfgroupmember : aero.api.sfgroupmember,
            folder : aero.api.folder,
            file : aero.api.file,

            // v1.4 Routes not exposed in public SDK
            getLinks : function(sid, headers) {
                return $q.when(aero.client.get('/shares/' + sid + '/urls', headers));
            },

            getSharedFolderGroupMembers : function(sid, gid) {
                return $q.when(aero.client.get('/shares/' + sid + '/groups/' + gid));
            },

            shareExistingFolder : function(sid) {
                return $q.when(aero.client.put('/folders/' + sid + '/is_shared'));
            },

            config : aero.config
        };
    }
]);

shelobServices.factory('MyStores', ['$log', 'API', function($log, API) {
    var _root = null;
    var _managedShares = null;

    function _getRoot() {
        if (_root === null) {
            _root = API.sf.getMetadata('root')
                .then(function(response) {
                    $log.debug("got root store id: ", response.data.id);
                    return response.data.id;
            });
        }
        return _root;
    }

    function _getShares() {
        _managedShares = API.sf.list('me')
            .then(function(response) {
                var managed = [];
                for (var i = 0; i < response.data.length; i++) {
                    if (response.data[i].caller_effective_permissions.indexOf('MANAGE') > -1) {
                        managed.push(response.data[i].id);
                    }
                }
                return {managed: managed, all: response.data};
            });
        return _managedShares;
    }

    return {
        // return the user's root SID
        getRoot: _getRoot,

        // return a list of the SIDs of the user's shared folders where the user has
        // MANAGE privileges.
        // N.B. does NOT include the user's root store
        getShares: _getShares,
    }
}]);
