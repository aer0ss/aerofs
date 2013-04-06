#! /usr/bin/env groovy

import groovy.grape.Grape

Grape.addResolver( [name:'central', root:'http://repos.arrowfs.org/nexus/content/repositories/central/'] )
Grape.addResolver( [name:'snapshots', root:'http://repos.arrowfs.org/nexus/content/repositories/snapshots/'] )
Grape.grab( [group:'org.arrowfs', module:'arrow-config', version:'latest.snapshot'] )
