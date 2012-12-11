class servlet::syncstat(
    $mysql_sp_password,
    $mysql_endpoint,
    $verkehr_host,
    $cacert_location
) {
    class{"servlet":
        proxy_read_timeout => "60",
        proxy_send_timeout => "60"
    }

    package{"aerofs-syncstat":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    package{"aerofs-syncstat-tools":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    class{"servlet::config::syncstat":
        mysql_sp_password       => $mysql_sp_password,
        mysql_endpoint          => $mysql_endpoint,
        verkehr_host            => $verkehr_host,
        cacert_location         => $cacert_location
    }

    # Java heap space for tomcat.
    line{ "tomcat java heap space":
         ensure => present,
         file => "/etc/default/tomcat",
         line => "JAVA_OPTS=\"-Djava.awt.headless=true -Xmx512m -XX:+UseConcMarkSweepGC\"",
         require => Package["aerofs-syncstat"]
    }
}
