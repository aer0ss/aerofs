"finder" is an empty Cocoa application, except for the "sync" target.  Only the
application extension "sync.appex" is included in the AeroFS application, but
without a Cocoa application, it's impossible to debug the appex in XCode.

To test: open finder.xcworkspace in XCode, and run the "sync" target.  The appex
will not work if there's no Gui for it to talk to on the native socket.
