class analytics {
    $analytics_dir = "/opt/analytics"

    $analytics_mysql = hiera("analytics_mysql")

    $mysql_user = $analytics_mysql[user]
    $mysql_passwd = $analytics_mysql[password]
    $mysql_host = hiera("mysql_endpoint")

    file { "${analytics_dir}":
        source  =>  "puppet:///modules/analytics/",
        recurse =>  true,
        ensure  =>  directory,
        mode    =>  "700",
        owner   =>  "root",
        group   =>  "root"
    }

    file { "${analytics_dir}/analytics.sql":
        content => template("analytics/analytics.sql.erb"),
        mode    => "755",
        owner   => "root",
        group   => "root"
    }

    file { "/usr/local/bin/support_updates":
        content => template("analytics/support_updates.erb"),
        mode    => "755",
        owner   => "root",
        group   => "root"
    }

    cron { "mysql_analytics":
        command => "mysql -u root analytics < ${analytics_dir}/analytics.sql",
        user    => "root",
        hour    => 13
    }
}
