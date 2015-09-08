include ../../Makefile.base

IMAGE_NAME = aerofs/identity
PACKAGE_NAME = aerofs.com/identity
TAG = alpha

GOCKERIZE_ARGS = GOARGS="-tags aero"
GOCKERIZE_CTX = ..

run:
	go install
	../../../bin/identity -swagger=$(PWD)/swagger-ui/dist -cfg=false -host=localhost -port=8000
