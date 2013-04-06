#!/usr/bin/env groovy

@Grab( 'org.arrowfs:arrow-config:latest.snapshot' )
import org.arrowfs.config.ArrowConfiguration
import org.arrowfs.config.sources.PropertiesConfiguration

def path = args[ 0 ]
def key = args[ 1 ]

ArrowConfiguration.initialize( ArrowConfiguration.builder()
        .addConfiguration( PropertiesConfiguration.newInstance( [ path ] ), "static-properties" )
        .build() )

println ArrowConfiguration.getInstance().getString( key )
