define logrotate::log(
    $filename,
    $quantity = 5,
    $frequency = "daily",
    $compress = false
) {

    include logrotate

    file{"/etc/logrotate.d/${title}":
        owner => root,
        group => root,
        mode => 644,
        content => template("logrotate/logrotate.erb"),
        require => File["/etc/logrotate.d"]
    }
}
