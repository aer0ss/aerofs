# Basic Puppet manifest for syncdet VM

class syncdet-packages {
    exec {"apt_update":
        command => "/usr/bin/apt-get update"
    }

    package {[
        "python-pip",
        "python-setuptools",
        "python-software-properties", # For add-apt-repository.
        "default-jre-headless",
        "avahi-daemon",
        "ntp",
        "sqlite3"
    ]:
        ensure => installed,
        require => Exec["apt_update"],
    }
    service {"avahi-daemon":
        ensure => running
    }
    package {"PyYAML":
        ensure => installed,
        provider => "pip",
    }
    package {"protobuf":
        ensure => installed,
        provider => "pip"
    }
    package {"requests":
        ensure => "1.1.0",
        provider => "pip"
    }

#     # propagate local prod IPs from host
#     host { "unified.syncfs.com":
#         ensure => present,
#         target => "/etc/hosts",
#         ip => $::unified_ip
#     }

    file{"/home/aerofstest":
        ensure => directory,
        owner => "aerofstest",
        group => "aerofstest"
    }
    user{"aerofstest":
        ensure => present,
        shell => "/bin/bash"
    }
}

class syncdet {
    include syncdet-packages

    # copy user public key from host for passwordless ssh
    ssh_authorized_key{"aerofstest":
        ensure => present,
        user => "aerofstest",
        key => $::my_ssh_key,
        type => "ssh-rsa"
    }

    # copy CI build agent public key from host for passwordless ssh
    ssh_authorized_key{"agent-vagrant":
        ensure => present,
        user => "aerofstest",
        key => $::agent_vagrant_ssh_key,
        type => "ssh-rsa"
    }
}

include syncdet
