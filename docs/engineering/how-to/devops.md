# DevOps

Contact Matt or Drew with questions.

## Monitoring Systems

We use PagerDuty for production system monitoring. Scripts on z.arrowfs.org
run from a cron check our systems. When things go wrong, the scripts email
PagerDuty which triggers on-call engineer notifications.

Engineers can configure their notification rules on our
[pagerduty subdomain](https://aerofs.pagerduty.com).

Suggestion: use something that will wake you up and get past your "do not
disturb" phone settings, e.g. 1 text and 5 consecutive phone calls.

## System Access

All on-call engineers should be given ssh access to production systems.

Access to AWS and Digital Ocean is restricted. Only a subset of engineers will
be given access.

## Services

### Monitored

| Service | Platform | Description |
|-|-|-|
| API | AWS | Havre, Bifrost, Sparta; facilitates API access. |
| Dryad | AWS | Log collection. |
| PrivateCloud | AWS | Licensing service. |
| Rocklog | AWS | Hybrid Cloud events service. |
| SP | AWS | User management features. |
| Vekrehr | AWS | Push notifications. |
| Web | AWS | Web admin panel. |
| X | Digital Ocean | Ejabberd and presence. |
| Zephyr | Digital Ocean | Relay service. |

### Blind Spots

TODO: fix it fix it.

| Service | Platform | Description |
|-|-|-|
| Charlie | AWS | Checkin service. |
| Config | AWS | Config service. |

## Debugging and Correction

Stanard "work, dammit" techniques:

1. Look in `/var/log/<service>/`, see if there is something obviously wrong.
2. Do `service <service> restart`.
3. Restart the machine (if you have access).

TODO: add more better stanard debugging techniques.
