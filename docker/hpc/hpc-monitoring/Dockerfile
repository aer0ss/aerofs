FROM alpine:3.3

MAINTAINER AeroFS "support@aerofs.com"

EXPOSE 5000

RUN apk --update add \
       bash \
       curl \
       python \
       perl-datetime \
       perl-lwp-protocol-https \
       python-dev \
       py-pip \
       py-psutil \
       unzip &&\

    rm -rf /var/cache/apk/*

RUN curl http://aws-cloudwatch.s3.amazonaws.com/downloads/CloudWatchMonitoringScripts-1.2.1.zip -O &&\
    unzip CloudWatchMonitoringScripts-1.2.1.zip && rm CloudWatchMonitoringScripts-1.2.1.zip

RUN echo "*/5 * * * * /aws-scripts-mon/mon-put-instance-data.pl --mem-util --mem-used --mem-avail \
--auto-scaling=only --aws-credential-file='/home/core/aws_credentials'" >> /monitoring_taks &&\
    crontab /monitoring_taks

CMD ["/start_process.sh"]

ADD root /

RUN pip install -r /requirements.txt
