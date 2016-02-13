FROM nginx:aerofs

# Ensure this folder exists to save us work modifying the image later.
RUN mkdir -p /etc/nginx/sites-disabled

COPY root /

EXPOSE 80 443 4433 5222

ENTRYPOINT [ "/container-scripts/service-barrier" ]

CMD [ "/run.sh" ]
