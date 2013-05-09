class pd-mysql-redis {
    include private-common

    # MySQL client and MySQL server.
    include mysql
    include mysql::server

    # Redis in AOF (append only file) mode.
    include redis::aof

    # Should get pulled via apt dependency, but add it here just for good
    # measure.
    package {"aerofs-spdb":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    file {"/opt/bootstrap/bootstrap.tasks":
        source => "puppet:///modules/pd-mysql-redis/bootstrap.tasks",
        require => Package["aerofs-bootstrap"],
    }
}

