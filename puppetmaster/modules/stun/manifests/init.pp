class stun {
    package{[
        "aerofs-stun",
    ]:
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    file {"/etc/restund.conf":
        source => "puppet:///modules/stun/restund.conf",
        require => Package["aerofs-stun"],
    }

    file{"/etc/init.d/restund":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => File["/etc/restund.conf"],
    }

    service { "restund":
        ensure => running,
        provider => upstart,
        require => File["/etc/init.d/restund"],
    }
}
