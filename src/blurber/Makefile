PKG_NAME	= aeroim
IMG_NAME	= blurber
IMAGE_NAME  = $(PKG_NAME)/$(IMG_NAME)
TAG			= v0.0.5
REGISTRY_PUSH = registry.aerofs.com:5050/$(IMAGE_NAME)

CONT_DIR    = .
BUILDROOT   = .
SRC_FILES	+= $(CONT_DIR)/Dockerfile

all: image

image:
	docker build -t $(IMAGE_NAME) $(CONT_DIR)

push: image
	docker tag -f $(IMAGE_NAME) $(REGISTRY_PUSH):$(TAG)
	docker push $(REGISTRY_PUSH):$(TAG)

.PHONY: all image clean push
