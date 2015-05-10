IMAGE_NAME = aerofs/probe

SOCKET_MAPPING=/var/run/docker.sock:/var/run/docker.sock

image:
	docker run -v $(SOCKET_MAPPING) -v $(CURDIR)/src:/gopath/src/probe --rm aerofs/golang-builder \
		$(IMAGE_NAME) probe

.PHONY: image
