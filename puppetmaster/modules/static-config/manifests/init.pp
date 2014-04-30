class static-config {
    # Yes, it really is just a file in a folder at a well-known path.
    # Be careful adding other files with sensitive information though:
    # all files under /opt/staticconfig are currently public.
    file {"/opt/staticconfig":
        ensure => directory,
        mode => "755",
        owner => "root",
        group => "root",
    }
    file {"/opt/staticconfig/client":
        ensure => present,
        source => "puppet:///modules/static-config/client",
        require => File["/opt/staticconfig"],
        owner => "root",
        group => "root",
        mode => "644",
    }
}
