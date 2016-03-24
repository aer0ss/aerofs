FROM aerofs/nginx

# Ensure this folder exists to save us work modifying the image later.
RUN mkdir -p /etc/nginx/sites-disabled

EXPOSE 80 443 4433 5222

CMD [ "/run.sh" ]

COPY root /
