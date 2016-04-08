DEST = $(abspath ../../../../resource/updater)
SRC = $(wildcard *.go)

BUILD_FLAGS = -a -installsuffix static -ldflags -d -ldflags -s -ldflags -w
GOPATH = $(abspath ../../../)
GOBUILD = GOPATH=$(GOPATH) CGOENABLED=0 go build $(BUILD_FLAGS)


all: $(DEST)/linux/updater-i386 \
	 $(DEST)/linux/updater-amd64 \
	 $(DEST)/osx/updater \
	 $(DEST)/win/updater.exe

$(DEST)/linux/updater-amd64: $(SRC)
	GOOS=linux GOARCH=amd64 $(GOBUILD) -o "$@" aerofs.com/updater

$(DEST)/linux/updater-i386: $(SRC)
	GOOS=linux GOARCH=386 $(GOBUILD) -o "$@" aerofs.com/updater

$(DEST)/osx/updater: $(SRC)
	GOOS=darwin GOARCH=amd64 $(GOBUILD) -o "$@" aerofs.com/updater

$(DEST)/win/updater.exe: $(SRC)
	GOOS=windows GOARCH=386 $(GOBUILD) -o "$@" aerofs.com/updater
