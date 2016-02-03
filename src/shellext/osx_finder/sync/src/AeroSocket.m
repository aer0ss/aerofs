#import "AeroSocket.h"
#import "../../../osx_common/gen/Shellext.pb.h"
#import "AeroClient.h"
#import "AeroFinderSync.h"
#import <sys/un.h>
#import <sys/socket.h>

#define NO_TIMEOUT               -1
#define DEFAULT_TIMEOUT           3  // seconds
#define MAX_MESSAGE_LENGTH        1*1024*1024 // 1 MB

// Private definitions:

typedef enum {
    kReadingLength,
    kReadingData
} ReadingState;

@interface AeroSocket (Private)
-(void) reconnect;
-(void) listenForMessage;
-(void) onSocketDidDisconnect:(GCDAsyncSocket*) sock;
@end

uint32_t readInt(NSData* data);

static BOOL shouldReconnectIfDisconnected = NO;

@implementation AeroSocket

-(void) dealloc
{
    [self disconnect];
}

-(void) connectToServerOnSocket:(NSString*) theSockFile
{
    sockFile = [[NSURL URLWithString:theSockFile] copy];
    [self reconnect];
}

-(void) reconnect
{
    @try {
        [self disconnect];
        asyncSocket = [[GCDAsyncSocket alloc] initWithDelegate:self delegateQueue:dispatch_get_main_queue()];
        [asyncSocket connectToUrl:sockFile withTimeout:DEFAULT_TIMEOUT error:nil];
        [self listenForMessage];
    }
    @catch (NSException *exception) {
        if (shouldReconnectIfDisconnected) {
            [self reconnect];
        }
    }
}

/**
 Disconnects and releases the AsyncSocket
 Make sure we won't automatically try reconnect
*/
-(void) disconnect
{
    shouldReconnectIfDisconnected = NO;
    if (asyncSocket != nil) {
        [asyncSocket setDelegate:nil];
        [asyncSocket setDelegateQueue:nil];
        [asyncSocket disconnect];
        asyncSocket = nil;
    }
}

/**
 * Send a protobuf call to the AeroFS gui.
 * Will fail if the socket isn't connected
 */
-(void) sendMessage:(ShellextCall*)call
{
    NSData* msg = [call data];
    uint32_t size = htonl([msg length]);
    NSMutableData* data = [NSMutableData dataWithBytes: &size length:sizeof(uint32_t)];
    [data appendData:msg];
    [asyncSocket writeData:data withTimeout:3 tag:0];
}

-(BOOL) isConnected
{
    return [asyncSocket isConnected];
}

/**
 Read a message from the server
 We should always be waiting for a message, otherwise we won't be notified of disconnections until we try to write
 */
-(void) listenForMessage
{
    [asyncSocket readDataToLength:sizeof(uint32_t) withTimeout:NO_TIMEOUT tag:kReadingLength];
}

/**
 Helper function to return the first 32 bits of a NSData as an unsigned int.
 Returns 0 if there is any error
 */
uint32_t readInt(NSData* data)
{
    if([data length] < sizeof(uint32_t)) return 0;

    const uint32_t* pResult = [data bytes];
    if (pResult == nil) return 0;

    return ntohl(*pResult);
}

/**
 Begin: Callbacks defined in GCDAsyncSocket.h
 */

- (void)socket:(GCDAsyncSocket *)sock didConnectToUrl:(NSURL *)url
{
    NSLog(@"AeroFS: Connection successful on socket file: %@.", sockFile);
    shouldReconnectIfDisconnected = YES;
    if(![[AeroClient instance] rootAnchor])
        [[AeroClient instance] sendGreeting];
    else
        [AeroFinderSync initMyFolderURL];
}

- (void)socketDidDisconnect:(GCDAsyncSocket*) sock withError:(NSError*) err;
{
    NSLog(@"AeroFS: Socket disconnected.");

    if (shouldReconnectIfDisconnected) {
        NSLog(@"Reconnecting...");
        [self reconnect];
    } else {
        [self disconnect];
        NSLog(@"AeroFS: disconnected.  Terminating Finder Sync Extension.");
        [[NSApplication sharedApplication] terminate:nil];
    }
}

/**
 Called by AsyncSocket when we received data
 tag is a value from the ReadingState enum that tells in which state we are of the reading process
*/
- (void)socket:(GCDAsyncSocket*) sock didReadData:(NSData*) data withTag:(long)tag;
{
    uint32_t length;

    @try {
        switch ((ReadingState) tag) {
        case kReadingLength:
            length = readInt(data);
            if (length == 0 || length > MAX_MESSAGE_LENGTH) {
                NSLog(@"AeroFS: Received an invalid message (length: %u bytes) - disconnecting", length);
                [self disconnect];
                break;
            }
            [asyncSocket readDataToLength:length withTimeout:NO_TIMEOUT tag:kReadingData];
            break;

        case kReadingData:
            [[AeroClient instance] parseNotification:[ShellextNotification parseFromData:data]];
            // Wait for the next message
            [self listenForMessage];
            break;
        }
    } @catch (NSException* e) {
        NSLog(@"AeroFS: Exception while trying to parse protobuf data. Disconnecting");
        [self disconnect];
    }
}

@end
