define logrotate::log(
    $filename = "/var/log/${title}/*.log",
    $quantity = 7,
    $frequency = "daily",
    $compress = true
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
