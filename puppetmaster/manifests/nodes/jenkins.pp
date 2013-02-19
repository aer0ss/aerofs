node "jenkins.arrowfs.org" inherits default {
    users::add_user {
        [ hiera('dev_users') ]:
    }

    apt::source { "jenkins":
        location    => "https://pkg.jenkins-ci.org/debian",
        release     => "binary/",
        repos       => "",
        key         => "D50582E6",
        key_source  => "https://pkg.jenkins-ci.org/debian/jenkins-ci.org.key",
        include_src => false
    }

    package {"jenkins":
        ensure => installed,
        require => Apt::Source["jenkins"]
    }

    include nginx

    nginx::resource::vhost {"${fqdn}":
        listen_port          => '80',
        proxy                => 'http://127.0.0.1:8000',
        ensure               => present,
    }
}
