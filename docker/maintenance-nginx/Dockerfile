FROM nginx:aerofs

COPY root buildroot /

EXPOSE 80 443

ENTRYPOINT [ "/container-scripts/service-barrier" ]

CMD [ "/run.sh" ]
