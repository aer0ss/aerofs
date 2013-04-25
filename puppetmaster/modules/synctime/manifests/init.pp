class synctime {
    package { "aerofs-synctime":
        ensure  => latest,
        require => Apt::Source["aerofs"]
    } 

    service {"synctime":
        ensure => running,
        provider => upstart,
        require => Package["aerofs-synctime"]
    }
}
