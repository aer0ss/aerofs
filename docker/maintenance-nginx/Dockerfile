FROM nginx:aerofs

EXPOSE 80 443

ENTRYPOINT [ "/container-scripts/service-barrier" ]
CMD [ "/run.sh" ]

COPY root buildroot /
