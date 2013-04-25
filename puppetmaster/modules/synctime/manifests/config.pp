class synctime::config(
    $mysql_config
) {
    file{"/opt/synctime/synctime.yml":
        content => template("synctime/synctime.yml.erb"),
        require => Package["aerofs-synctime"],
        notify  => Service["synctime"]
    }
}
