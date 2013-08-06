class sanity {
    package{[
        "aerofs-sanity",
    ]:
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    file{"/etc/init.d/sanity":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => Package["aerofs-sanity"],
    }

    service { "sanity":
        enable => true,
        ensure => running,
        provider => upstart,
        require => File["/etc/init.d/sanity"],
    }
}
