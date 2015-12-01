FROM scratch
ADD bin/sloth /sloth
ADD src/aerofs.com/sloth/migration /migration
EXPOSE 8001
CMD ["/sloth", "-host=0.0.0.0", "-dbHost=mysql.service"]
