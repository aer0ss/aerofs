node "sv.aerofs.com" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    class{"servlet::sv":
        mysql_password => hiera("mysql_password"),
        mysql_endpoint => hiera("mysql_endpoint")
    }

    include mailserver

    class{"analytics":}

    cron{"remove old defects":
        command => 'find /var/svlogs_prod/defect/ -mtime +28 -iname log.defect\* | xargs rm',
        hour    => "0",
        minute  => "0"
    }

    cron{"remove empty defects":
        command => 'find /var/svlogs_prod/defect/ -iname log.defect\* -size 0 | xargs rm',
        hour    => "0",
        minute  => "0"
    }

    cron{"remove old logs":
        command => 'find /var/svlogs_prod/archived/ -iname \*.gz -mtime +80 | xargs rm',
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
}
