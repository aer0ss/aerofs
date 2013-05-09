define servlet::log(
    $config_filename,
    $log_level
) {
    $log_filename = $title

    file {$config_filename:
        ensure  => present,
        owner   => "tomcat6",
        group   => "tomcat6",
        mode    => "644",
        content => template("servlet/logback.xml.erb"),
        require => File["/var/log/aerofs"],
        notify  => Service["tomcat6"]
    }
}
