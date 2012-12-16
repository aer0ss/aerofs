class dbtools {

    include servlet::tools

    package{"python-mysqldb":
        ensure => latest,
    }
    config_entry{"sp_database":
        value => hiera("sp_database")
    }
    config_entry{"sv_database":
        value => hiera("sv_database")
    }
    credential{"SV read only":
        username => "aerofs_sv_ro",
        password => hiera("aerofs_sv_ro_password")
    }
    credential{"SP read only":
        username => "aerofs_sp_ro",
        password => hiera("aerofs_sp_ro_password")
    }
}

define credential(
    $username,
    $password,
) {
    augeas{"aerofsdb_conf_${username}":
        changes => "set ${username}_password ${password}",
        lens => "Shellvars.lns",
        incl => "/etc/aerofsdb.conf",
    }
}

define config_entry(
    $key = $title,
    $value
) {
    augeas{"aerofsdb_conf_${title}":
        changes => "set ${key} ${value}",
        lens => "Shellvars.lns",
        incl => "/etc/aerofsdb.conf",
    }
}
