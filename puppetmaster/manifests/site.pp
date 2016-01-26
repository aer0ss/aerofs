# FIXME mkfs module went missing. Disable for now.
#include mkfs

node default {
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

    $repo = hiera("environment","") ? {
        "staging"   => "staging",
        default     => "prod"
    }

    # Common package for for apt, logs, service management, etc.
    class{"common":
        # TODO: in puppet >2.7, this can be file() instead of template()
        aptkey => template('common/aerofs-apt-key'),
        repo => $repo
    }

    # Must have the puppet service running.
    include puppet

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

    file{"/etc/ssl/certs/AeroFS_CA.pem":
        source  => "puppet:///aerofs_cacert/cacert.pem",
        ensure  => present
    }
}

import "nodes/*.pp"
