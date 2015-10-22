## To run a test with Karma:

- navigate to this directory
- run `npm install` to install the dependencies listed in package.json
- run `karma start <path-to/karma.conf.*.js>` to run the tests as described in
  the karma configuration file. For example:
  karma start shelob/karma.conf.*.js

## To run all tests with Karma:
- navigate to this directory
- run `npm install` to install the dependencies listed in package.json
- run .run_tests.sh to run all tests in this directory. If you add a new test,
  please add it to the shell script until there is a better way to do it.

  or

- run ./invoke test_js from project home


## For more information

See the angularjs tutorial: http://docs.angularjs.org/tutorial/

