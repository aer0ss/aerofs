FROM redis

MAINTAINER AeroFS "support@aerofs.com"

CMD ["/container-scripts/restart-on-error", "redis-server", "--appendonly", "yes"]

COPY buildroot /
