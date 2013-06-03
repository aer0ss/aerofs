class stun {
    package{[
        "aerofs-stun",
    ]:
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    file{"/etc/init.d/restund":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => Package["aerofs-stun"],
    }
    
    service { "restund":
        ensure => running,
        provider => upstart,
        require => File["/etc/init.d/restund"],
    }
}
