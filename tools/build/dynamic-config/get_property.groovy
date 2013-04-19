#!/usr/bin/env groovy

@Grab( 'com.aerofs.dynamic-config:dynamic-config-client:0.1' )
import com.aerofs.config.DynamicConfiguration
import com.aerofs.config.sources.PropertiesConfiguration

def path = args[ 0 ]
def key = args[ 1 ]

DynamicConfiguration.initialize( DynamicConfiguration.builder()
        .addConfiguration( PropertiesConfiguration.newInstance( [ path ] ), "static-properties" )
        .build() )

println DynamicConfiguration.getInstance().getString( key )
