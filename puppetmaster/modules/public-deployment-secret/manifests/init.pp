class public-deployment-secret {
    file {"/data/deployment_secret":
        ensure => present,
        source => "puppet:///aerofs_ssl/deployment_secret",
        owner => "root",
        group => "root",
        mode => "644",
    }
}
