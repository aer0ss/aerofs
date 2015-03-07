class slack(
    $token
) {
    $slack_message = "/usr/local/bin/slack_message"
    $default_room = 93246
    file { $slack_message:
        source => "puppet:///modules/slack/slack_message",
        ensure => present,
        mode => "755"
    }
}
