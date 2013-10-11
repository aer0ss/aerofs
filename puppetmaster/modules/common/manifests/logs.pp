class common::logs {
    file {"/var/log":
        ensure => directory
    }

    file {"/var/log/aerofs":
        ensure => directory,
        require => File["/var/log"],
        mode    => 755,
    }

    logrotate::log{"aerofs": }
}
