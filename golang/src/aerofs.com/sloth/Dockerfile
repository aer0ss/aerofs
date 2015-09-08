FROM scratch

EXPOSE 8001

CMD ["/sloth", "-host=0.0.0.0", "-dbHost=mysql.service"]

COPY src/aerofs.com/sloth/migration /migration
ADD bin/sloth /sloth
