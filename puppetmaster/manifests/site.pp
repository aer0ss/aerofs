include mkfs

node default {
    include motd
    include common::firewall
    include puppet
    include common::logs

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

    package { [
        "default-jdk",
        "htop",
        "dstat",
        "ntp",
        "iftop"
        ]:
        ensure => latest,
    }

    $repo = hiera("environment","") ? {
        "staging"   => "staging",
        default     => "production"
    }

    $aptkey = hiera("aptkey")

    apt::source { "aerofs":
        location    => "http://apt.aerofs.com/ubuntu/${repo}",
        repos       => "main",
        include_src => false,
        key         => "${aptkey}",
        key_server  => "pgp.mit.edu",
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
