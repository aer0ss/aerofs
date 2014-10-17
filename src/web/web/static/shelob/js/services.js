var shelobServices = angular.module('shelobServices', ['shelobConfig']);

// This service should be used to keep track of the number of outstanding AJAX
// calls so that a spinner can be shown when there are requests outstanding.
// Call push() when a request is made, and pop() when the response is received
shelobServices.factory('OutstandingRequestsCounter', [
    function() {
        var counter = 0;
        return {
            push: function() { counter += 1; return counter; },
            pop: function() { counter -= 1; return counter; },
            get: function() { return counter; }
        };
    }
]);

shelobServices.factory('Token', ['$http', '$q', '$log', 'OutstandingRequestsCounter',
    function($http, $q, $log, OutstandingRequestsCounter) {

    var token = null;
    var outstandingNewTokenRequest = false;

    return {

    // Get a token from the server, or use a locally cached token
    //
    // If there is already an outstanding request for a token, wait for and use
    // the result of that request instead of making a new one. The variable "token"
    // will be a promise that will be resolved with the token when the response is
    // received, so we return that promise.
    get: function() {
        if (token === null) {
            token = $q.defer();
            OutstandingRequestsCounter.push();
            $http.get('/json_token?t=' + Math.random())
                .success(function (data) {
                    token.resolve(data.token);
                })
                .error(function (data, status) {
                    token.reject({data: data, status: status});
                })["finally"](OutstandingRequestsCounter.pop);
        }
        return $q.when(token.promise);
    },

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
            OutstandingRequestsCounter.push();
            $http.get('/json_new_token')
                .success(function (data) {
                    token.resolve(data.token);
                })
                .error(function (data, status) {
                    token.reject({data: data, status: status});
                })
                ["finally"](function() {
                    outstandingNewTokenRequest = false;
                    OutstandingRequestsCounter.pop();
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
shelobServices.factory('API', ['$http', '$q', '$log', 'Token', 'API_LOCATION', 'OutstandingRequestsCounter',
    function($http, $q, $log, Token, API_LOCATION, OutstandingRequestCounter) {

        function _request(config) {
            var deferred = $q.defer();

            /* Two arguments: a token and an optional boolean
            that will stop the function from retrying fetching a token 
            from the Token service */
            var doStuffWithToken = function (token, doNotRetry) {
                config.headers.Authorization = 'Bearer ' + token;
                config.url = API_LOCATION + '/api/v' + (config.version || '1.2') + config.path;
                // if data is an array buffer, send the bytes directly. Otherwise, allow
                // angular to apply the default transforms (handling encoding, etc.)
                config.transformRequest = function(data) {
                    if (window.ArrayBuffer && data instanceof ArrayBuffer) return data;
                    return $http.defaults.transformRequest[0](data);
                };
                OutstandingRequestCounter.push();
                $http(config)
                    .success(function (data, status, headers) {
                        // if the call succeeds, return the data
                        deferred.resolve({data: data, headers: headers});
                    })
                    .error(function (response, status) {
                        if (status == 401 && !doNotRetry) {
                            // if the call got 401, the token may have expired, so try a new one
                            Token.getNew().then(function (token) {
                                /* Do not retry if the second attempt fails */
                                doStuffWithToken(token, true);
                            }, function (status) {
                                // this is called if getting a new token fails
                                // consider this an internal server error and return a 500
                                deferred.reject({status: 500});
                            });
                        } else {
                            // if the call failed with anything other than 401, return the error code
                            // Also, if doNotRetry is true (because we explicitly passed in a token),
                            // this could be a 401 error
                            deferred.reject({data: response, status: status});
                        }
                    })["finally"](OutstandingRequestCounter.pop);
            };

            // get an OAuth token and make call
            if (config.headers.token) {
                doStuffWithToken(config.headers.token, true);
            } else {
                $log.debug('No token provided manually.');
                Token.get().then(doStuffWithToken, function (status) {
                    // this is called if Token.get() fails
                    // consider this an internal server error and return 500
                    deferred.reject({status: 500});
                });
            }
            return deferred.promise;
        }

        function _get(path, headers, config) {
            config = config || {};
            config.method = 'GET';
            config.path = path;
            config.headers = headers || {};
            return _request(config);
        }

        function _delete(path, headers, config) {
            config = config || {};
            config.method = 'DELETE';
            config.path = path;
            config.headers = headers || {};
            return _request(config);
        }

        function _head(path, headers, config) {
            config = config || {};
            config.method = 'HEAD';
            config.path = path;
            config.headers = headers || {};
            return _request(config);
        }

        function _put(path, data, headers, config) {
            config = config || {};
            config.method = 'PUT';
            config.path = path;
            config.data = data;
            config.headers = headers || {};
            return _request(config);
        }

        function _post(path, data, headers, config) {
            config = config || {};
            config.method = 'POST';
            config.path = path;
            config.data = data;
            config.headers = headers || {};
            return _request(config);
        }

        function _chunkedUpload(oid, file, chunkSize, headers, config) {
            chunkSize = chunkSize || 512 * 1024;
            headers = headers || {};
            headers['Content-Type'] = 'application/octet-stream';

            var deferred = $q.defer();

            function readAndUploadChunk(start) {
                var reader = new FileReader();
                var end = (start + chunkSize > file.size) ? file.size - 1 : start + chunkSize - 1;
                var blob;
                if (file.slice) blob = file.slice(start, end + 1);
                else if (file.mozSlice) blob = file.mozSlice(start, end + 1);
                else if (file.webkitSlice) blob = file.webkitSlice(start, end + 1);
                else deferred.reject({reason: 'compatibility', message: 'file.slice does not exist'});
                reader.onload = function(e) {
                    var bytes = e.target.result;
                    headers['Content-Range'] = 'bytes ' + start + '-' + end + '/' + file.size;
                    headers['Endpoint-Consistency'] = 'strict';
                    _put('/files/' + oid + '/content', bytes, headers, config).then(function (response) {
                        $log.info("put succeeded", response);
                        deferred.notify({progress: (end + 1) / file.size});
                        if (start + chunkSize < file.size) {
                            headers['Upload-ID'] = response.headers('Upload-ID');
                            headers['If-Match'] = response.headers('ETag');
                            readAndUploadChunk(start + chunkSize);
                        } else {
                            deferred.resolve({uploaded: end + 1});
                        }
                    }, function(response) {
                        // PUT failed
                        $log.error('PUT failed', response);
                        deferred.reject({reason: 'upload', status: response.status});
                    });
                };
                reader.onerror = function(e) {
                    // read from disk failed
                    $log.error('read from disk failed', e);
                    deferred.reject({reason: 'read', event: e});
                };
                reader.readAsArrayBuffer(blob);
            }

            if (file.size === 0) {
                deferred.resolve({uploaded: 0});
            } else {
                readAndUploadChunk(0);
            }

            return deferred.promise;
        }

        return {
            // Make basic requests to the API server
            //
            // Arguments:
            //   path: API path, prepended with a slash (e.g. /children or /folders/:id)
            //   data (put, post only): data to be included in the request body
            //   headers: associative array of http headers
            //   config: $http config object (http://docs.angularjs.org/api/ng/service/$http)
            //
            // Response:
            //   the object passed to the success callback has 'data' and 'headers' attributes
            //   the object passed to the failure callback has 'data' and 'status' attributes
            //
            get: _get,
            'delete': _delete,
            head: _head,
            put: _put,
            post: _post,

            // Performs a chunked upload by repeatedly calling the put() method
            //
            // Arguments:
            //   oid: the id of the file whose content should be written to
            //   file: a JS Blob or File object which can be read with a FileReader
            //   chunkSize: the size of the chunk in bytes
            //   data (put, post only): data to be included in the request body
            //   headers: associative array of http headers
            //   config: $http config object (http://docs.angularjs.org/api/ng/service/$http)
            //
            // Response:
            //   the object passed to the success callback has the following attributes:
            //          uploaded: the number of bytes that were uploaded
            //   the object passed to the failure callback has the following attributes:
            //          reason: a single word describing the type of failure (e.g. 'upload' or 'read')
            //          status (if reason == 'upload'): http status code with which the upload failed
            //          event (if reason == 'read'): the event which FileReader passes to the failure callback
            //   the object passed to the notify callback has the following attributes:
            //          progress: number between 0 and 1 indicating the upload progress
            //
            chunkedUpload: _chunkedUpload,
        };
    }
]);

