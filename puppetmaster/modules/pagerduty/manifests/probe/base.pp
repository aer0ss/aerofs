# specific probes can be wrappers around pagerduty::probe::base, so if we want
# to provide pagerduty::probe::url or pagerduty::probe::df90 it would be simple.
define pagerduty::probe::base(
  $command = $title,
  $hour,
  $minute
) {
  cron{$command:
    command => "/opt/aerofs.pagerduty/probe ${command}",
    user => root,
    hour => $hour,
    minute => $minute
  }
}
