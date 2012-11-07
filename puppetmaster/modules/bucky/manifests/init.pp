class bucky {
    package{"python-pip":
        ensure => latest
    }
    package{"bucky":
        ensure => latest,
        provider => "pip",
        require => Package["python-pip"]
    }
    service{"bucky":
        ensure => running,
        require => File["/etc/init/bucky.conf"],
        provider => "upstart"
    }
    file{"/etc/init/bucky.conf":
        source => "puppet:///modules/bucky/bucky.conf",
        notify => Service["bucky"],
        mode => 755
    }
}
