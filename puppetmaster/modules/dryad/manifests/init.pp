class dryad {
    common::service { "dryad": }

    file { "/data":
        ensure  => directory,
        owner   => "dryad",
        group   => "dryad",
        mode    => "0755",
    }

    # We keep 90 dayds worth of logs. Clean once a day, at noon.
    cron { "remove old dryad logs":
        command => "/usr/bin/clean-dryad-logs 90",
        minute  => "0",
        hour    => "12",
        require => Package["aerofs-dryad"],
    }
}
