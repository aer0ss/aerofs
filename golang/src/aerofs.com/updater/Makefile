DEST = $(abspath ../../../../resource/updater)
SRC = $(wildcard *.go)

COMMIT = $(shell git rev-parse HEAD)
TIMESTAMP = $(shell date -u '+%Y-%m-%dT%H:%M:%SZ')
BUILD_FLAGS = -a -installsuffix static
LDFLAGS = -w -X main.buildtime=$(TIMESTAMP) -X main.commithash=$(COMMIT)
GOPATH = $(abspath ../../../)
GOBUILD = GOPATH=$(GOPATH) go build $(BUILD_FLAGS)


all: osx win linux

.PHONY:

osx: $(DEST)/osx/updater $(DEST)/osx/updater-arm64
win: $(DEST)/win/updater.exe $(DEST)/win/updater64.exe
linux: $(DEST)/linux/updater-amd64 $(DEST)/linux/updater-i386

$(DEST)/linux/updater-amd64: $(SRC)
	GOOS=linux GOARCH=amd64 CGO_ENABLED=0 $(GOBUILD) -ldflags "-s -d $(LDFLAGS)" -o "$@" aerofs.com/updater

$(DEST)/linux/updater-i386: $(SRC)
	GOOS=linux GOARCH=386 CGO_ENABLED=0 $(GOBUILD) -ldflags "-s -d $(LDFLAGS)" -o "$@" aerofs.com/updater

# NB: use CGO to prevent up to .5s delay on startup for system cert verification
$(DEST)/osx/updater: $(SRC)
	GOOS=darwin GOARCH=amd64 CGO_ENABLED=1 $(GOBUILD) -ldflags "-s $(LDFLAGS)" -o "$@" aerofs.com/updater

# NB: use CGO to prevent up to .5s delay on startup for system cert verification
$(DEST)/osx/updater-arm64: $(SRC)
	GOOS=darwin GOARCH=arm64 CGO_ENABLED=1 $(GOBUILD) -ldflags "-s $(LDFLAGS)" -o "$@" aerofs.com/updater

$(DEST)/win/updater.exe: $(SRC)
	# NB: do not store icon w/ .syso ext to prevent it from being picked up in *nix build
	cp icon icon.syso
	GOOS=windows GOARCH=386 CGO_ENABLED=0 $(GOBUILD) -ldflags "$(LDFLAGS) -H windowsgui" -o "$@" aerofs.com/updater
	rm icon.syso

$(DEST)/win/updater64.exe: $(SRC)
	# NB: do not store icon w/ .syso ext to prevent it from being picked up in *nix build
	cp icon icon.syso
	GOOS=windows GOARCH=amd64 CGO_ENABLED=0 $(GOBUILD) -ldflags "$(LDFLAGS) -H windowsgui" -o "$@" aerofs.com/updater
	rm icon.syso
