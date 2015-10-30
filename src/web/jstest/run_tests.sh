#!/bin/bash
set -e
cd $(dirname $0)

# TODO: Streamline this since it will get annoying really quickly (MB)
./node_modules/.bin/karma start shelob/karma.conf.unit.js
./node_modules/.bin/karma start shelob/karma.conf.e2e.js
./node_modules/.bin/karma start strider/karma.conf.unit.js
./node_modules/.bin/karma start ng-modules/karma.conf.unit.js
