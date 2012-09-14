class common::logs {
    file {"/var/log":
        ensure => directory
    }

    file {"/var/log/aerofs":
        ensure => directory,
        require => File["/var/log"],
        mode    => 666,
    }

    logrotate::log{"standard_aerofs_logs":
        filename => "/var/log/aerofs/*.log",
        quantity => 7,
        frequency => "daily",
        compress => true,
    }
}
