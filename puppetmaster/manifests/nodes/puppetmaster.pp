node "puppet" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }
    # PH: including puppet::master::config overrides puppet::config
    # I'm not sure why but just including puppet::master doesn't work
    include puppet::master::config
    include puppet::master

    # these files need to be writable by the admin group in order to deploy
    # updated puppet scripts
    file{[
        "/etc/puppet/manifests",
        "/etc/puppet/modules"
        ] :
        ensure => directory,
        recurse => true,
        group => "admin",
        mode => "664"
    }

}
