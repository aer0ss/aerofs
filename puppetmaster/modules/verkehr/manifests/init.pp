class verkehr {
    common::service{"verkehr": }

    file { '/data/topics':
        ensure => 'directory',
        owner => 'verkehr',
        group => 'verkehr',
        mode => 755,
        require => Package['aerofs-verkehr'],
    }
}
