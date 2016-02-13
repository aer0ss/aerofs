PKG_NAME	= aeroim
IMG_NAME	= blurber
IMAGE_NAME  = $(PKG_NAME)/$(IMG_NAME)

all: image

image:
	docker build -t $(IMAGE_NAME) .

.PHONY: all image
