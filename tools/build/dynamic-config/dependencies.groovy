#! /usr/bin/env groovy

import groovy.grape.Grape

Grape.addResolver( [name:'central', root:'http://repos.arrowfs.org/nexus/content/repositories/central/'] )
Grape.addResolver( [name:'releases', root:'http://repos.arrowfs.org/nexus/content/repositories/releases/'] )
Grape.grab( [group: 'com.aerofs.dynamic-config', module:'dynamic-config-client', version: '0.1'] )
