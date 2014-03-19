node "sv.aerofs.com" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    include public-email-creds

    # install sv servlet
    include servlet
    class{"servlet::config::sv":
        mysql_password => hiera("mysql_password"),
        mysql_endpoint => hiera("mysql_endpoint")
    }

    include mailserver

    class{"analytics":}

    cron{"remove old defects":
        command => '/usr/bin/clean_defects && symlinks -dr /var/svlogs_prod/defect > /dev/null 2>&1',
        minute  => "0",
        # runs 4 times a day and at 23h UTC which is 1-2h before the pagerduty probe fires
        hour    => "5,11,17,23",
    }
    cron{"remove empty defects":
        command => 'find /var/svlogs_prod/defect/ -iname log.defect\* -size 0 | xargs rm',
        hour    => "0",
        minute  => "0"
    }
    cron{"remove old logs":
        command => 'find /var/svlogs_prod/archived/ -iname \*.gz -mtime +60 | xargs rm',
        hour    => "0",
        minute  => "0"
    }

    include proguard

    file { "/maps":
        ensure => directory,
        mode   => 2775,
        group  => "admin",
        owner  => "root",
    }

    package { "gnuplot":
        ensure => installed
    }
    # Used to clean up dangling symlinks left by clean_defects
    package { "symlinks":
        ensure => installed
    }
}
