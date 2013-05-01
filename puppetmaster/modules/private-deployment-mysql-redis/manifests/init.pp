class private-deployment-mysql-redis {

    $key = '64E72541'
    apt::source { "aerofs":
        location    => "http://apt.aerofs.com/ubuntu/production",
        repos       => "main",
        include_src => false,
        key         => $key,
        key_server  => "pgp.mit.edu",
    }

    include mysql
    include redis::aof
}

