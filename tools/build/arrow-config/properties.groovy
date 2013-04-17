#!/usr/bin/env groovy

@Grapes([
  @GrabResolver(name='central', root='http://repos.arrowfs.org/nexus/content/repositories/central/'),
  @GrabResolver(name='apache-snapshots', root='http://repos.arrowfs.org/nexus/content/repositories/apache-snapshots/'),
  @GrabResolver(name='codehaus-snapshots', root='http://repos.arrowfs.org/nexus/content/repositories/codehaus-snapshots/'),
  @GrabResolver(name='google-api-client-libraries', root='http://repos.arrowfs.org/nexus/content/repositories/google-api-client-libraries/'),
  @GrabResolver(name='thirdparty', root='http://repos.arrowfs.org/nexus/content/repositories/thirdparty/'),
  @GrabResolver(name='releases', root='http://repos.arrowfs.org/nexus/content/repositories/releases/'),
  @GrabResolver(name='snapshots', root='http://repos.arrowfs.org/nexus/content/repositories/snapshots/'),
  @Grab('org.arrowfs:arrow-config:12-SNAPSHOT')
])
import org.arrowfs.config.ArrowConfiguration
import org.arrowfs.config.sources.PropertiesConfiguration

def path = args[ 0 ]
def key = args[ 1 ]

ArrowConfiguration.initialize( ArrowConfiguration.builder()
        .addConfiguration( PropertiesConfiguration.newInstance( [ path ] ), "static-properties" )
        .build() )

println ArrowConfiguration.getInstance().getString( key )
