class motd (
    $template = 'motd/motd.erb'
) {
    file { '/etc/motd':
        ensure  => file,
        backup  => false,
        content => template($template),
    }
}
