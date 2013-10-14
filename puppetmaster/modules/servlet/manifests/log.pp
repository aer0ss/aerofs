define servlet::log(
    $config_filename,
    $log_level,
    $log_filename = "/var/log/tomcat6/${title}.log"
) {
    file {$config_filename:
        ensure  => present,
        owner   => "tomcat6",
        group   => "tomcat6",
        mode    => "644",
        content => template("servlet/logback.xml.erb"),
        notify  => Service["tomcat6"]
    }

    logrotate::log{"${title}":
        filename => $log_filename,
    }
}
