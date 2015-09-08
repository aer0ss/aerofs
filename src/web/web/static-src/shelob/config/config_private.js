// To use these config values, the module must be dependent on shelobConfig
// and the value(s) you want to use must be injected.
//
// For instance, to use the API_LOCATION config value in the API factory, the
// 'shelobServices' module must include 'shelobConfig' in its dependency list,
// and the API factory must include 'API_LOCATION'.
// If private, use web host.
angular.module('shelobConfig', [])
    .constant('API_LOCATION', '')
    .constant('IS_PRIVATE', true);