IMAGE_NAME = aerofs/linux64
COMMAND = /bin/bash


run: image
	docker run --rm -it -v ~/repos:/mnt/repos $(IMAGE_NAME) $(COMMAND)


image:
	docker build -t $(IMAGE_NAME) .
