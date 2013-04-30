class verkehr::ssl {
    $aerofs_ssl_dir = hiera("environment","") ? {
        "staging"   => "aerofs_ssl/staging",
        default     => "aerofs_ssl"
    }

    file {"/opt/verkehr/verkehr.key":
        ensure  => present,
        owner   => "verkehr",
        group   => "verkehr",
        mode    => "0400",
        source  => "puppet:///${aerofs_ssl_dir}/verkehr.key",
        require => Package["aerofs-verkehr"],
        notify  => Service["verkehr"]
    }

    file {"/opt/verkehr/verkehr.crt":
        ensure  => present,
        owner   => "verkehr",
        group   => "verkehr",
        mode    => "0400",
        source  => "puppet:///${aerofs_ssl_dir}/verkehr.crt",
        require => Package["aerofs-verkehr"],
        notify  => Service["verkehr"]
    }
}
