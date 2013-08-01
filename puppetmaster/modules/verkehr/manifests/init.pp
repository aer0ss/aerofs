class verkehr {
    package { "aerofs-verkehr":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    file{"/etc/init.d/verkehr":
        ensure => link,
        target => "/lib/init/upstart-job",
        require => Package["aerofs-verkehr"],
    }

    service { "verkehr":
        tag => ['autostart-overridable'],
        enable => true,
        ensure => running,
        provider => upstart,
        require => File["/etc/init.d/verkehr"],
    }
}
