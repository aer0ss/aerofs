define servlet::config::file(
    $content
) {
    file {$title:
        ensure  => present,
        owner   => "root",
        group   => "tomcat6",
        mode    => "644",
        content => $content,
        notify  => Service["tomcat6"]
    }
}
