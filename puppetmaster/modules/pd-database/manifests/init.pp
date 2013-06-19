class pd-database {
    include private-common

    # --------------
    # MySQL
    # --------------

    # MySQL client and MySQL server.
    include mysql
    include mysql::server

    # Should get pulled via apt dependency, but add it here just for good
    # measure.
    package {"aerofs-spdb":
        ensure  => latest,
        require => Apt::Source["aerofs"],
    }

    # --------------
    # Redis
    # --------------

    # Redis in AOF (append only file) mode.
    include redis::aof

    # --------------
    # Bootstrap
    # --------------

    file {"/opt/bootstrap/bootstrap.tasks":
        source => "puppet:///modules/pd-database/bootstrap.tasks",
        require => Package["aerofs-bootstrap"],
    }

    # --------------
    # Sanity
    # --------------

    file {"/opt/sanity/probes/mysql.sh":
        source => "puppet:///modules/pd-database/probes/mysql.sh",
        require => Package["aerofs-sanity"],
    }

    file {"/opt/sanity/probes/redis.sh":
        source => "puppet:///modules/pd-database/probes/redis.sh",
        require => Package["aerofs-sanity"],
    }
}
