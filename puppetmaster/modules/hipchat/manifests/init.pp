class hipchat(
    $token
) {
    $hipchat_room_message = "/usr/local/bin/hipchat_room_message"
    $default_room = 93246
    file { $hipchat_room_message:
        source => "puppet:///modules/hipchat/hipchat_room_message",
        ensure => present,
        mode => "755"
    }
}
