#import <Carbon/Carbon.h>
#import "AppleEventConstants.h"
#import <Cocoa/Cocoa.h>

bool waitUntilFinderIsRunning(void);

int main(int argc, char *argv[])
{
    NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];

    if (!waitUntilFinderIsRunning()) {
        NSLog(@"AeroFS: timed out while waiting for the Finder to run");
        // but there's no reason not to try to inject it anyway, so let's go ahead and try
    }

    // Get the port number from the command line
    int portNumber = 0;
    if (argc>=2) {
        portNumber = atoi(argv[1]);
    }

    // Create an Apple Event targeting the Finder
    NSAppleEventDescriptor* finder = [NSAppleEventDescriptor descriptorWithDescriptorType:typeApplicationBundleID data:[FINDER_BUNDLE_ID dataUsingEncoding:NSUTF8StringEncoding]];

    // Send the Finder a kASAppleScriptSuite / kGetAEUT event.
    // This will load the scripting additions into the Finder and make sure it's synchronized with the contents of the scripting additions folder.
    // See:  http://developer.apple.com/library/mac/#qa/qa1070/_index.html
    NSAppleEventDescriptor* refresh = [NSAppleEventDescriptor appleEventWithEventClass:kASAppleScriptSuite eventID:kGetAEUT targetDescriptor:finder returnID:kAutoGenerateReturnID transactionID:kAnyTransactionID];
    AESendMessage([refresh aeDesc], NULL, kAEWaitReply | kAENeverInteract | kAEDontRecord, kAEDefaultTimeout);

    // Now send our own 'aeroload' event with the portnumber
    NSAppleEventDescriptor* aeroLoad = [NSAppleEventDescriptor appleEventWithEventClass:INJECT_EVENT_CLASS eventID:INJECT_EVENT_ID targetDescriptor:finder returnID:kAutoGenerateReturnID transactionID:kAnyTransactionID];
    [aeroLoad setParamDescriptor:[NSAppleEventDescriptor descriptorWithInt32:portNumber] forKeyword:PORT_KEYWORD];
    AESendMessage([aeroLoad aeDesc], NULL, kAENoReply | kAENeverInteract | kAEDontRecord, kAEDefaultTimeout);

    [pool release];

    return 0;
}

/**
* Waits until the Finder is running, or until we timeout
* Returns true if the Finder is running, false we timed out and the Finder is still not running
*
* Note: currently, the timeout is set to 10 minutes
*/
bool waitUntilFinderIsRunning(void)
{
    const NSTimeInterval retryInterval = 0.1;  // interval before checking again if Finder is running
    NSTimeInterval waitedSeconds = 0;          // seconds elapsed since we started checking for the Finder
    const NSTimeInterval maxWait = 10*60;      // maximum amount of seconds we will wait for the Finder

    while (waitedSeconds < maxWait) {
        NSArray* runningFinder = [NSRunningApplication runningApplicationsWithBundleIdentifier:FINDER_BUNDLE_ID];
        if (runningFinder.count > 0) {
            return true;
        }
        [NSThread sleepForTimeInterval:retryInterval];
        waitedSeconds += retryInterval;
    }
    return false;
}