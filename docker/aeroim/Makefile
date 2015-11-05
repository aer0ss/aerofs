#
# Pull AeroIM required images in from the aerofs registry.
#
# This makefile is a placeholder. As we move aeroim content into this project, they can
# be built directly here. For now, we will assume that "I don't care about this detail"
# means "I can accept the last version tagged and pushed to registry.aerofs.com".
#
image: pull_images
	$(NOP)

REGISTRY=registry.aerofs.com
TAG=latest

IMAGES += aeroim/blurber
IMAGES += aeroim/nginx
IMAGES += aeroim/sloth

.PHONY: $(IMAGES)

$(IMAGES):
	docker pull $(REGISTRY)/$@:$(TAG)
	docker tag -f $(REGISTRY)/$@:$(TAG) $@

pull_images: $(IMAGES)

build:
	make -C ../../../blurber image
	make -C ../../../sloth image
	make -C ../../../aeroim-client image

.PHONY: pull_images build image

	
