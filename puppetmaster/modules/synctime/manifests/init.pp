class synctime {
    package { "aerofs-synctime":
        ensure  => latest,
        require => Apt::Source["aerofs"]
    } 

    service {"synctime":
        ensure => running,
        require => Package["aerofs-synctime"]
    }
}
