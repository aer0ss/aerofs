class common::log_mount(
    $partition
) {

    # FIXME mkfs module went missing. Disable this for now.
    #mkfs::create{$partition:
    #    type        => "ext4",
    #    partition   => $partition,
    #}

    file {"/data":
        ensure  => directory,
        owner   => root,
        group   => root,
    }

    # FIXME mkfs module went missing. Disable this for now.
    #mount {"/data":
    #    atboot  => true,
    #    device  => $partition,
    #    ensure  => mounted,
    #    fstype  => "ext4",
    #    options => "defaults",
    #    dump    => "0",
    #    pass    => "0",
    #    require => [
    #        Mkfs::Create[$partition],
    #        File["/data"]
    #    ],
    #}

    file {"/data/var":
        ensure => directory,
        owner   => root,
        group   => root,
        require => Mount["/data"]
    }

    file {"/data/var/log":
        ensure => directory,
        owner   => root,
        group   => root,
        require => File["/data/var"]
    }

    file {"/data/var/log/aerofs":
        ensure => directory,
        mode    => 666,
        require => [
            File["/data/var/log"],
            Package["tomcat6"]
        ],
    }

    mount { "/var/log/aerofs":
        atboot => true,
        device => "/data/var/log/aerofs",
        ensure  => mounted,
        fstype  => "none",
        options => "bind",
        require => File["/data/var/log/aerofs","/var/log/aerofs"],
    }
}
