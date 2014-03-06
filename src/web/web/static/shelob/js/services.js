var shelobServices = angular.module('shelobServices', []);

shelobServices.factory('Token', ['$http', '$q', '$log',
    function($http, $q, $log) { return {

    // get a token from the server, or use a locally cached token
    get: function() {
        var deferred = $q.defer();
        $http.get('/json_token', {cache: true})
          .success(function(data, status, headers, config) {
              deferred.resolve(data.token);
          })
          .error(function(data, status) {
              deferred.reject(status);
          });
        return deferred.promise;
    },

    // get a brand new OAuth token from the server
    getNew: function() {
        var deferred = $q.defer();
        $http.get('/json_new_token')
          .success(function(data, status, headers, config) {
              deferred.resolve(data.token);
          })
          .error(function(data, status) {
              deferred.reject(status);
          });
        return deferred.promise;
    }
}}]);

shelobServices.factory('API', ['$http', '$q', '$log', 'Token',
    function($http, $q, $log, Token) {

      var request = function(method, path, headers) {
          var deferred = $q.defer();

          // get an OAuth token and make call
          Token.get().then(function(token) {
              if (headers === undefined) headers = {};
              headers.Authorization = 'Bearer ' + token;
              $http({method: method, url: '/api/v1.0' + path, headers: headers})
                .success(function(data, status, headers, config) {
                    // if the call succeeds, return the data
                    deferred.resolve(data);
                })
                .error(function(data, status) {
                    if (status == 401) {
                        // if the call got 401, the token may have expired, so try a new one
                        Token.getNew().then(function(token) {
                          headers.Authorization = 'Bearer ' + token;
                          $http({method: method, url: '/api/v1.0' + path, headers: headers})
                            .success(function(data, status, headers, config) {
                                // if the call succeeds, return the data
                                deferred.resolve(data);
                            })
                            .error(function(data, status) {
                                // if the call fails with the new token, stop trying and return the status code
                                deferred.reject(status);
                            });
                        }, function(status) {
                            // this is called if getting a new token fails
                            // consider this an internal server error and return a 500
                            deferred.reject(500);
                        });
                    } else {
                        // if the call failed with anything other than 401, return the error code
                        deferred.reject(status);
                    }
                });
          }, function(status) {
              // this is called if Token.get() fails
              // consider this an internal server error and return 500
              deferred.reject(500);
          });
          return deferred.promise;
      };

      return {
          // path arg must be prepended with a slash
          // i.e. call with '/children', not 'children'
          get: function(path, headers) { return request('GET', path, headers); },
          head: function(path, headers) { return request('HEAD', path, headers); }
    }}
]);

