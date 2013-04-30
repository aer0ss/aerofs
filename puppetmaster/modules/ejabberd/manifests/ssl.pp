class ejabberd::ssl {
    $aerofs_ssl_dir = hiera("environment","") ? {
        "staging"   => "aerofs_ssl/staging",
        default     => "aerofs_ssl"
    }

    file { "/etc/ejabberd/ejabberd.pem":
        ensure  => "present",
        owner   => "root",
        group   => "ejabberd",
        mode    => "640",
        source  => "puppet:///${aerofs_ssl_dir}/ejabberd.pem",
        require => Package["ejabberd"],
        notify  => Service["ejabberd"],
    }
}
