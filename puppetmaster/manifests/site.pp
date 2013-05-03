include mkfs

node default {
    $repo = hiera("environment","") ? {
        "staging"   => "staging",
        default     => "production"
    }

    class{"common":
        aptkey => hiera("aptkey"),
        repo => $repo
    }

    # remove default user
    user { "ubuntu":
        ensure => absent
    }

    group { "admin":
        ensure => present
    }

    # run apt-get update every time.
    exec{"apt-get update":
        logoutput => "on_failure",
    }

    # include cacert
    $ca_cert_filename = hiera("environment","") ? {
        "staging"   => "cacert-staging.pem",
        default     => "cacert.pem"
    }
    file{"/etc/ssl/certs/AeroFS_CA.pem":
        source  => "puppet:///aerofs_cacert/${ca_cert_filename}",
        ensure  => present
    }

    # These defaults ensure that the persistence command (in common::firewall)
    # is executed after every change to the firewall
    Firewall {
        notify  => Exec['persist-firewall'],
    }

    class{"collectd":
        prefix => hiera("deployment_config")
    }

    include bucky
}

import "nodes/*.pp"
