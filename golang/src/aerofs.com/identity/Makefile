IMAGE_NAME = aerofs/identity
TAG = alpha
REGISTRY_PUSH = registry.aerofs.com:5050

image:
	GOARGS="-tags aero" ../../../gockerize/gockerize $(IMAGE_NAME) aerofs.com/identity ..

push: image
	docker tag -f $(IMAGE_NAME) $(REGISTRY_PUSH)/$(IMAGE_NAME):$(TAG)
	docker push $(REGISTRY_PUSH)/$(IMAGE_NAME):$(TAG)

.PHONY: image push
