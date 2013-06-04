class ca::autostart inherits ca {
    file {"/etc/init/ca-server.conf":
        source => "puppet:///modules/ca/ca-server.conf",
        require => Package["aerofs-ca-server"],
    }

    file{"/etc/init.d/ca-server":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => File["/etc/init/ca-server.conf"],
    }

    service { "ca-server":
        enable => true,
        ensure => running,
        provider => upstart,
        require => File["/etc/init.d/ca-server"],
    }
}
