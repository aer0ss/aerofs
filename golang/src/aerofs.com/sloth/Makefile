# recognize third party deps in vendor/ dir
# must use go v1.5 or greater
export GO15VENDOREXPERIMENT = 1

GOCKERIZE_CTX = ..
GOCKERIZE_DIR = ../../../gockerize
GO_PKG_NAME   = aerofs.com/sloth
IMAGE_NAME 	  = aeroim/sloth
REGISTRY_PUSH = registry.aerofs.com:5050/$(IMAGE_NAME):$(TAG)
TAG 		  = latest

all: image

sloth: *.go
	go build

image: sloth
	$(GOCKERIZE_DIR)/gockerize $(IMAGE_NAME) $(GO_PKG_NAME) $(GOCKERIZE_CTX)

push:
	docker tag -f $(IMAGE_NAME) $(REGISTRY_PUSH)
	docker push $(REGISTRY_PUSH)

clean:
	rm -f ./sloth

.phony: image push clean
