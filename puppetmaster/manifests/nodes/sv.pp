node "sv.aerofs.com" inherits default {

    users::add_user {
        [ hiera('dev_users') ]:
    }

    # install sp servlet
    class{"servlet":
        metrics      => hiera("metrics"),
        tomcat6_user => hiera("tomcat6_manager")
    }
    include nginx
    include servlet::nginx
    class{"servlet::config::sv":
        mysql_password => hiera("mysql_password"),
        mysql_endpoint => hiera("mysql_endpoint")
    }

    include mailserver

    class{"analytics":}

    cron{"remove old defects":
        command => '/usr/bin/clean_defects',
        minute  => "0",
        hour    => "*",
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
}
