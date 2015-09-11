IMAGE_NAME = aerofs/identity
TAG = alpha
REGISTRY_PUSH = registry.aerofs.com:5050

image:
	GOARGS="-tags aero" ../../../gockerize/gockerize $(IMAGE_NAME) aerofs.com/identity ..

push: image
	docker tag -f $(IMAGE_NAME) $(REGISTRY_PUSH)/$(IMAGE_NAME):$(TAG)
	docker push $(REGISTRY_PUSH)/$(IMAGE_NAME):$(TAG)

run:
	go install
	../../../bin/identity -swagger=$(PWD)/swagger-ui/dist -cfg=false -host=localhost -port=8000

.PHONY: image push
