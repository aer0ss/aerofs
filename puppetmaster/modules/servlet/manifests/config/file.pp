define servlet::config::file(
    $content
) {
    file {$title:
        ensure  => present,
        owner   => "root",
        group   => "tomcat6",
        mode    => "644",
        content => $content,
        require => File["/etc/ssl/certs/AeroFS_CA.pem"],
        notify  => Service["tomcat6"]
    }
}
