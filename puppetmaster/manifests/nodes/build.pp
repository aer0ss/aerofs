node "build.arrowfs.org" inherits default {
    package {
        [
            "nsis",
            "git",
            "zip",
            "fakeroot",
            "libqt4-dev",
            "g++",
            "make",
            "mono-devel",
            "lftp",
        ]:
            ensure => installed,
    }

    mkfs::create{ "ext4 /dev/xvdf":
            type      => "ext4",
            partition => "/dev/xvdf",
    }

    file { "/data":
            ensure => directory,
            owner  => root,
            group  => root,
    }

    mount { "/data":
            atboot  => true,
            device  => "/dev/xvdf",
            ensure  => mounted,
            fstype  => "ext4",
            options => "defaults",
            dump    => "0",
            pass    => "0",
            require => [ Mkfs::Create["ext4 /dev/xvdf"], File["/data"]],
    }

    add_build_user { "staging":
        ssh_keys => hiera("dev_users_ssh_keys"),
    }

    add_build_user { "prod":
        ssh_keys => [
                        hiera('weihanw_ssh_keys'),
                        hiera('yuris_ssh_keys'),
                    ],
    }

    users::add_user {
                [ hiera('dev_users') ]:
                        }

    $s3cmdver = "1.1.0-beta3"

    exec { "install-s3cmd":
        command => "wget -O /tmp/s3cmd.tar.gz 'http://downloads.sourceforge.net/project/s3tools/s3cmd/1.1.0-beta3/s3cmd-1.1.0-beta3.tar.gz?r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fs3tools%2Ffiles%2Fs3cmd%2F1.1.0-beta3%2F&ts=1340128658&use_mirror=superb-sea2' \
                    && tar -zxvf /tmp/s3cmd.tar.gz -C /tmp \
                    && cd /tmp/s3cmd-1.1.0-beta3 \
                    && python setup.py install \
                    && echo ${s3cmdver} > /var/log/puppet/s3cmd.ver",
        unless => "cat /var/log/puppet/s3cmd.ver | grep ${s3cmdver}",
    }

    file { "/home/prod/.lftp/login.cfg":
        ensure => present,
        owner  => "prod",
        group  => "prod",
        source => "puppet:///arrowfs_build/prod/login.cfg",
        mode   => "0700",
    }
}
define add_build_user($ssh_keys) {

    $username = $title

    $userdata = {
        username => $username,
        realname => "${username} build account",
        shell    => "/bin/bash",
        home     => "/home/${username}",
        ssh_keys => $ssh_keys,
    }

    users::add_user{ "${username}": 
        userdata => $userdata,
    }

    file { "/data/${username}-downloads":
        ensure  => directory,
        owner   => "${username}",
        group   => "${username}",
        require => [ Users::Add_user["${username}"], Mount["/data"] ];
    }

    file { "/home/${username}/.certs":
        ensure  => directory,
        owner   => "${username}",
        group   => "${username}",
        mode    => "0600",
        require => Users::Add_user["${username}"], 
    }

    file { "/home/${username}/.certs/cert.spc":
        ensure => present,
        owner  => "${username}",
        group  => "${username}",
        mode   => "0400",
        source => "puppet:///arrowfs_build/${username}/certs/cert.spc";
    }

    file { "/home/${username}/.certs/privateKey.pvk":
        ensure => present,
        owner  => "${username}",
        group  => "${username}",
        mode   => "0400",
        source => "puppet:///arrowfs_build/${username}/certs/privateKey.pvk",
    }
}
