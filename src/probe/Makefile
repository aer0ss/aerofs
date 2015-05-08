IMAGE_NAME = aerofs/probe

SOCKET_MAPPING=/var/run/docker.sock:/var/run/docker.sock
DOCKER_MAPPING=/usr/local/bin/docker:/usr/local/bin/docker

image:
	docker build -t $(IMAGE_NAME)-builder .
	docker run -v $(SOCKET_MAPPING) -v $(DOCKER_MAPPING) -it --rm $(IMAGE_NAME)-builder
	docker rmi -f $(IMAGE_NAME)-builder

.PHONY: image
