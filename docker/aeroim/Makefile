# TODO: This makefile is a placeholder. As we move aeroim content into this
# project, they can be built directly here. For now, we will assume that "I
# don't care about this detail" means "I can accept the last version tagged and
# pushed to registry.aerofs.com".
include ../Makefile.base

IMAGE_NAME = aeroim/nginx

image:
	docker pull $(REGISTRY)/$(IMAGE_NAME):$(TAG)
	docker tag -f $(REGISTRY)/$(IMAGE_NAME):$(TAG) $(IMAGE_NAME)
	$(call success,"- pull $(IMAGE_NAME)")
