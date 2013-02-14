class proguard {

    package{"proguard":
        ensure => latest
    }

    file{"/usr/bin/retrace":
        source =>  "puppet:///modules/proguard/retrace",
        mode => 755,
    }

}
