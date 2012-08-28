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

    $repo = hiera("environment","") ? {
        "staging"   => "staging",
        default     => "production"
    }

    apt::source { "aerofs":
        location    => "http://apt.aerofs.com/ubuntu/${repo}",
        repos       => "main",
        include_src => false,
        key         => "64E72541",
        key_server  => "pgp.mit.edu",
    }

    #remove default user
    user { "ubuntu":
        ensure => absent
    }

    include puppet

    # run apt-get update every time.
    exec{"apt-get update":
        logoutput => "on_failure",
    }

    logrotate::log{"standard_aerofs_logs":
        filename => "/var/log/aerofs/*.log",
        quantity => 7,
        frequency => "daily",
        compress => true,
    }

    file{"/etc/ssl/certs/AeroFS_CA.pem":
        source  => "puppet:///aerofs_ssl/cacert.pem",
        ensure  => present
    }

}

import "nodes/*.pp"
