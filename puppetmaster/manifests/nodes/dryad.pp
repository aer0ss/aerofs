node /^dryad\.aerofs\.com$/ inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    users::add_user { "pagerduty" : }

    common::service{"dryad": }

    # We keep 90 days worth of logs. Clean once a day, at noon.
    cron{"remove old dryad logs":
        command => '/usr/bin/clean-dryad-logs 90',
        minute  => "0",
        hour    => "12",
    }
}
