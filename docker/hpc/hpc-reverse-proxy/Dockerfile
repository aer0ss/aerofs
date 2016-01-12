FROM nginx:1.9.5
MAINTAINER AeroFS "support@aerofs.com"

EXPOSE 80 443

# Mount the conf dir as a volume so that hpc-docker-gen can write the config there
VOLUME ["/etc/nginx/conf.d"]

ADD root/dhparam.pem /etc/nginx/dhparam.pem

# Start nginx
ADD root/run.sh /run.sh
CMD ["/run.sh"]
