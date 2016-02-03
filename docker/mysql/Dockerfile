FROM aerofs/base

RUN apk --update add mariadb mariadb-client && \
    rm -rf /var/cache/apk/*

COPY root /

VOLUME /var/lib/mysql

EXPOSE 3306

ENTRYPOINT [ "/container-scripts/restart-on-error", "/entrypoint.sh" ]
CMD [ "mysqld_safe" ]
