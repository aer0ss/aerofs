AeroFS Finder Extension
=======================

Setting up the tools
--------------------

To compile, you'll need Xcode and the protobuf Objective-C generator.

1. Xcode
Get in from the AppStore (4GB download).

2. Protobuf Objective-C generator:

   cd ~/repos
   git clone https://github.com/aerofs/protobuf-objc
   cd protobuf-objc
   ./autogen.sh
   ./configure
   make -j 8
   make install

You should now have the 'protoc-gen-objc' executable in /usr/local/bin

Making a release build
----------------------

	cd repos/aerofs
	./invoke proto
	cd src/shellext/osx_finder/tools
	./make-release

This will create 'AeroFSFinderExtension.osax' in aerofs/resource/client/osx

Making a debug build
--------------------

Use AppCode or Xcode to build FinderExtension.xcodeproj.
Make sure you get AeroFSFinderExtension.osax under build/Debug
THen use the ./inject script in 'tools' to inject the extension into the Finder.
