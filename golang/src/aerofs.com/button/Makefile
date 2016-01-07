# recognize third party deps in vendor/ dir
# must use go v1.5 or greater
export GO15VENDOREXPERIMENT = 1

GOCKERIZE_CTX = ../../
GOCKERIZE_DIR = ../../../gockerize
GO_PKG_NAME   = aerofs.com/button
IMAGE_NAME    = aeropush/button
REGISTRY_HOST = registry.aerofs.com
REGISTRY_PUSH = $(REGISTRY_HOST):5050/$(IMAGE_NAME):$(TAG)
TAG 		  = alpha

image:
	$(GOCKERIZE_DIR)/gockerize $(IMAGE_NAME) $(GO_PKG_NAME) $(GOCKERIZE_CTX)

push:
	docker tag -f $(IMAGE_NAME) $(REGISTRY_HOST):5050/$(IMAGE_NAME):$(TAG)
	docker push $(REGISTRY_PUSH)

.phony: image push
