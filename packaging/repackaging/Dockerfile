FROM aerofs/repackaging-base

COPY root buildroot /

EXPOSE 80

ENTRYPOINT [ "/container-scripts/service-barrier" ]

CMD [ "/run.sh" ]
