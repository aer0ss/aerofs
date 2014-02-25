// Karma configuration
// Generated on Wed Feb 19 2014 12:05:54 GMT-0800 (PST)

module.exports = function(config) {
  config.set({

    // base path, that will be used to resolve files and exclude
    basePath: '',


    // frameworks to use
    frameworks: ['ng-scenario'],


    preprocessors: {
        '**/*.html': []
    },


    // list of files / patterns to load in the browser
    files: [
      'e2e/scenarios.js',
      {pattern: 'e2e/**/*', watched: false, included: false, served: true},
      {pattern: 'lib/**/*', watched: false, included: false, served: true},
    ],

    // test results reporter to use
    // possible values: 'dots', 'progress', 'junit', 'growl', 'coverage'
    reporters: ['progress'],


    // web server port
    port: 9876,


    proxies: {
        '/index.html': 'http://localhost:9876/base/e2e/index.html',
        '/appTest.js': 'http://localhost:9876/base/e2e/appTest.js',
        '/lib/': 'http://localhost:9876/base/lib/',
        '/static/shelob/': 'http://localhost:9876/base/e2e/static/shelob/',
        '/static/js/': 'http://localhost:9876/base/e2e/static/js/',
    },


    // enable / disable colors in the output (reporters and logs)
    colors: true,


    // level of logging
    // possible values: config.LOG_DISABLE || config.LOG_ERROR || config.LOG_WARN || config.LOG_INFO || config.LOG_DEBUG
    logLevel: config.LOG_INFO,


    // enable / disable watching file and executing tests whenever any file changes
    autoWatch: false,


    // Start these browsers, currently available:
    // - Chrome
    // - ChromeCanary
    // - Firefox
    // - Opera (has to be installed with `npm install karma-opera-launcher`)
    // - Safari (only Mac; has to be installed with `npm install karma-safari-launcher`)
    // - PhantomJS
    // - IE (only Windows; has to be installed with `npm install karma-ie-launcher`)
    browsers: ['PhantomJS'],


    // If browser does not capture in given timeout [ms], kill it
    captureTimeout: 60000,

    // Continuous Integration mode
    // if true, it capture browsers, run tests and exit
    singleRun: true,

    plugins : [
        'karma-ng-scenario',
        'karma-phantomjs-launcher',
    ],
  });
};
