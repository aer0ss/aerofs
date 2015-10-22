#!/usr/bin/env bash

# TODO: Streamline this since it will get annoying really quickly (MB)
karma start shelob/karma.conf.unit.js
karma start shelob/karma.conf.e2e.js
karma start strider/karma.conf.unit.js
karma start ng-modules/karma.conf.unit.js