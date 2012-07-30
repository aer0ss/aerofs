include mkfs

node default {
include motd
    Exec {
        path => [
            '/usr/local/bin',
            '/opt/local/bin',
            '/usr/bin',
            '/usr/sbin',
            '/bin',
            '/sbin',],
        logoutput => true,
    }

    package { "default-jdk":
        ensure => latest,
    }

    package { "htop":
        ensure => latest,
    }

    package { "dstat":
        ensure => latest,
    }

#    class { "fwknop":
#        key => hiera("fwknop_pass"),
#    }

    apt::source { "aerofs":
        location    => "http://apt.aerofs.com/ubuntu/production",
        repos       => "main",
        include_src => false,
        key         => "64E72541",
        key_server  => "pgp.mit.edu",
    }

    #remove default user
    user { "ubuntu":
        ensure => absent
    }

    # to make this overridable (needed for the puppet master), we can't use
    # paramaterizable classes.  So we have to define the puppetmaster here
    # and include the puppet module
    $puppetmaster = "puppet"
    include puppet

    # run apt-get update every time.
    exec{"apt-get update":}
}

import "nodes/*.pp"
