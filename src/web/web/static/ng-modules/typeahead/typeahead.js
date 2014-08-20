var typeahead = angular.module('typeahead', []);
var getModuleBaseURL = function(scriptName) {
    currentScriptPath = document.querySelector("script[src$='" + scriptName + "']").src;
    return currentScriptPath.substring(0, currentScriptPath.lastIndexOf('/') + 1);
};

typeahead.directive('aeroTypeahead', ['$http', '$log',
    function($http, $log) {
    return {
        restrict: 'A',
        scope: {
            label: '@',
            placeholder: '@',
            disableRule: '=ngDisabled',
            asyncData: '@',
            asyncAttr: '@',
            typeaheadData: '=',
            maxResults: '@',
            selectedEntity: '=ngModel'
        },
        templateUrl: '/static/ng-modules/typeahead/typeahead.html',
        link: function($scope, element, attrs) {
            /* Spinner stuff */
            initializeSpinners();
            // only use spinners on modern browsers
            if (!$('html').is('.ie6, .ie7, .ie8')) {
                startSpinner($('#typeahead-spinner'), 3);
            }

            $scope.selectedEntity = {
                name: ''
            };

            var resetStatus = function(){
                $scope.activeIndex = 0;
                $scope.matches = [];
                $scope.loading = false;
                $scope.looking = true;
            };
            resetStatus();
            var maxResults = parseInt($scope.maxResults, 10) || 6;
            var matchCache = {};


            var lookup;
            if ($scope.asyncData) {
                $log.info('Typeahead relying on asynchronous data lookup.');
                var asyncAttr = $scope.asyncAttr || 'results';
                lookup = function(substring) {
                    // contact the provided URL with substring
                    $http.get($scope.asyncData, {
                        params: {
                            'substring': substring
                        }
                    }).success(function(response){
                        if (typeof(response[asyncAttr]) === 'object') {
                            matchCache[substring] = response[asyncAttr].slice(0, maxResults);
                            $scope.matches = matchCache[substring];
                        } else {
                            $log.error('Invalid response from server.');
                        }
                        $scope.loading = false;
                    }).error(function(response){
                        $log.error(response.status);
                        $scope.loading = false;
                    });
                };
            } else {
                $log.info('Typeahead relying on local data list.');

                lookup = function(substring){
                    // search on name attribute
                    // return up to maxResults number of matches
                    if (typeof($scope.typeaheadData) === 'object') {
                        $scope.matches = $scope.typeaheadData.filter(
                            function(item){
                                return item.name.toLowerCase().indexOf(substring.toLowerCase()) != -1;
                            }).slice(0, maxResults);
                    } else {
                        $log.info("No lookup completed; data source not found.");
                    }
                    $scope.loading = false;
                };
            }

            $scope.select = function(match) {
                angular.copy(match, $scope.selectedEntity);
                resetStatus();
                $scope.looking = false;
            };

            // Handler for keyboard events
            $scope.$onKeyDown = function(event) {
                $scope.$apply(function(){
                    if (!/(38|40|13)/.test(event.keyCode)) {
                        $scope.looking = true;
                        return;
                    }
                    // Let ngSubmit pass if the typeahead is hidden
                    if(!$scope.looking && $scope.matches.length && $scope.selectedEntity.name) {
                        event.preventDefault();
                        event.stopPropagation();
                    }
                    // Select with enter
                    if (event.keyCode === 13 && $scope.matches.length) {
                        $scope.select($scope.matches[$scope.activeIndex]);
                    } else {
                        // Navigate with keyboard
                        if (event.keyCode === 38 && $scope.activeIndex > 0) {
                            $scope.activeIndex--;
                        } else if (event.keyCode === 40 && $scope.activeIndex < $scope.matches.length - 1) {
                            $scope.activeIndex++;
                        } else if (angular.isUndefined($scope.activeIndex)) {
                            $scope.activeIndex = 0;
                        }
                    }
                });
            };
            // Apply listener for keyboard events
            element.on('keydown', $scope.$onKeyDown);

            $scope.$watch('selectedEntity.name', function(newValue, oldValue){
                if (oldValue != newValue && newValue && $scope.looking) {
                    resetStatus();
                    // only use cache if we're using async data
                    if ($scope.asyncData && matchCache && matchCache[newValue]) {
                        // we've looked for this value before
                        $scope.matches = matchCache[newValue];
                    } else {
                        $scope.loading = true;
                        lookup(newValue);
                    }
                }
            });
        }
    };
}]);