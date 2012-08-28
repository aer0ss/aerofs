class servlet::syncstat(
    $mysql_sp_password,
    $mysql_sp_username,
    $mysql_syncstat_password,
    $mysql_syncstat_username,
    $mysql_endpoint = undef
) {
    include servlet

    package{"aerofs-syncstat":
        ensure => latest,
        require => [
            Apt::Source["aerofs"]
        ]
    }

    file {"/usr/share/aerofs-syncstat/syncstat/WEB-INF/web.xml":
        ensure  => present,
        owner   => "root",
        group   => "tomcat6",
        mode    => "644",
        content => template("servlet/syncstat.web.xml.erb"),
        require => [
            Package["aerofs-syncstat"],
            File["/etc/ssl/certs/AeroFS_CA.pem"]
        ],
        notify  => Service["tomcat6"]
    }

    $log_filename = "/var/log/aerofs/syncstat_prod.log"
    file {"/usr/share/aerofs-syncstat/syncstat/WEB-INF/classes/log4j.properties":
        ensure  => present,
        owner   => "tomcat6",
        group   => "tomcat6",
        mode    => "644",
        content => template("servlet/log4j.properties.erb"),
        require => Package["aerofs-syncstat"],
        notify  => Service["tomcat6"]
    }
}
