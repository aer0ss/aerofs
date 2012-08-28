#
#  mounts $partition at /data
#  binds /data/var/log/aerofs to /var/log/aerofs
#
class servlet::mount (
    $partition
) {
    include servlet

    mkfs::create{$partition:
        type        => "ext4",
        partition   => $partition,
    }

    file {"/data":
        ensure  => directory,
        owner   => root,
        group   => root,
    }

    mount {"/data":
        atboot  => true,
        device  => $partition,
        ensure  => mounted,
        fstype  => "ext4",
        options => "defaults",
        dump    => "0",
        pass    => "0",
        require => [
            Mkfs::Create[$partition],
            File["/data"]
        ],
    }

    file {"/var/log":
        ensure => directory
    }

    file {"/var/log/aerofs":
        ensure => directory,
        require => File["/var/log"],
        owner   => "tomcat6",
        group   => "adm",
        mode    => "0750",
    }

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
        owner   => "tomcat6",
        group   => "adm",
        mode    => "0750",
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
        notify => Service["tomcat6"]
    }
}
