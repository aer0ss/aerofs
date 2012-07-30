node "puppetmaster" inherits default {

    # PH: including puppet::master::config overrides puppet::config
    # I'm not sure why but just including puppet::master doesn't work
    include puppet::master::config
    include puppet::master

    file{ "/etc/puppet" :
        ensure => directory,
        recurse => true,
        owner => "devops",
    }
}
