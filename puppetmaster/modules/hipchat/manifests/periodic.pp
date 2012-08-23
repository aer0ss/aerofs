define hipchat::periodic(
    $message = $title,
    $room = $::hipchat::default_room,
    $color = "white",
    $from,
    $hour,
    $minute
) {
    $token= $::hipchat::token
    $script = $::hipchat::hipchat_room_message
    $hipchat_command = "echo '${message}' | '${script}' -t '${token}' -r '${room}' -f '${from}' -c '${color}' -n"
    cron{$title:
        command => $hipchat_command,
        hour => $hour,
        minute => $minute
    }
}
