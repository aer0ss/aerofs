#import <Carbon/Carbon.h>
#import "AppleEventConstants.h"
#import <Cocoa/Cocoa.h>

bool waitUntilFinderIsRunning(void);

int main(int argc, char *argv[])
{
    @autoreleasepool {

        if (!waitUntilFinderIsRunning()) {
            NSLog(@"AeroFS: timed out while waiting for the Finder to run");
            // but there's no reason not to try to inject it anyway, so let's go ahead and try
        }

        // Get the socket file path from the command line
        NSString *socketFile;
        if (argc>=2) {
            socketFile = [NSString stringWithUTF8String: argv[1]];
        }

        // Create an Apple Event targeting the Finder
        // Note: there is a bug introduced in Mountain Lion that will make the AESendMessage call below hang and timeout if we use the Finder's bundle id
        // to deliver the event. However, using the process id works fine.
        // This is the same bug that prevents the "Show In Finder" functionality from working. Unfortunately, for that case we can't do anything since it
        // needs to be fixed by Apple. A workaround for it is to log out / log in or run "sudo killall -KILL appleeventsd" in the terminal
        // See: http://www.openradar.me/12424662
        // and: http://brian-webster.tumblr.com/post/32830692042/a-workaround-for-aesendmessage-hanging-on-os-x-10-8-2
        NSRunningApplication* runningApplication = [[NSRunningApplication runningApplicationsWithBundleIdentifier:FINDER_BUNDLE_ID] lastObject];
        pid_t pid = [runningApplication processIdentifier];
        NSAppleEventDescriptor* finder = [[NSAppleEventDescriptor alloc] initWithDescriptorType:typeKernelProcessID bytes:&pid length:sizeof(pid)];

        // Send the Finder a kASAppleScriptSuite / kGetAEUT event.
        // This will load the scripting additions into the Finder and make sure it's synchronized with the contents of the scripting additions folder.
        // See:  http://developer.apple.com/library/mac/#qa/qa1070/_index.html
        NSAppleEventDescriptor* refresh = [NSAppleEventDescriptor appleEventWithEventClass:kASAppleScriptSuite eventID:kGetAEUT targetDescriptor:finder returnID:kAutoGenerateReturnID transactionID:kAnyTransactionID];
        AESendMessage([refresh aeDesc], NULL, kAEWaitReply | kAENeverInteract | kAEDontRecord, kAEDefaultTimeout);

        // Now send our own 'aeroload' event with the portnumber
        NSAppleEventDescriptor* aeroLoad = [NSAppleEventDescriptor appleEventWithEventClass:INJECT_EVENT_CLASS eventID:INJECT_EVENT_ID targetDescriptor:finder returnID:kAutoGenerateReturnID transactionID:kAnyTransactionID];
        [aeroLoad setParamDescriptor:[NSAppleEventDescriptor descriptorWithString: socketFile] forKeyword: SOCK];
        AESendMessage([aeroLoad aeDesc], NULL, kAENoReply | kAENeverInteract | kAEDontRecord, kAEDefaultTimeout);

    }

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