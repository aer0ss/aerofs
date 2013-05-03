class pd-cfg-ca {

    $key = '64E72541'
    apt::source { "aerofs":
        location    => "http://apt.aerofs.com/ubuntu/production",
        repos       => "main",
        include_src => false,
        key         => $key,
        key_server  => "pgp.mit.edu",
    }

    # TODO (MP) need to add the configuration service here as well.
    include ca
}

