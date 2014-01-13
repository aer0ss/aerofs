#import "AeroSocket.h"
#import "../gen/Shellext.pb.h"
#import "AeroFinderExt.h"

#define NO_TIMEOUT               -1
#define DEFAULT_TIMEOUT           3  // seconds
#define MAX_MESSAGE_LENGTH        1*1024*1024 // 1 MB
#define LOCALHOST                 @"127.0.0.1"

// Private definitions:

typedef enum {
    kReadingLength,
    kReadingData
} ReadingState;

@interface AeroSocket (Private)
-(void) reconnect;
-(void) listenForMessage;
-(void) onSocketDidDisconnect:(AsyncSocket *)sock;
@end

uint32_t readInt(NSData* data);

static BOOL shouldReconnectIfDisconnected = NO;

@implementation AeroSocket

-(void) dealloc
{
    [self disconnect];
}

-(void) connectToServerOnPort:(UInt16) thePort
{
    port = thePort;
    [self reconnect];
}

-(void) reconnect
{
    [self disconnect];
    socket = [[AsyncSocket alloc] initWithDelegate:self];
    [socket connectToHost:LOCALHOST onPort:port withTimeout:DEFAULT_TIMEOUT error:nil];
    [self listenForMessage];
}

/**
 Disconnects and releases the AsyncSocket
 Make sure we won't automatically try reconnect
*/
-(void) disconnect
{
    shouldReconnectIfDisconnected = NO;
    [socket setDelegate:nil];
    [socket disconnect];
    socket = nil;
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
    [socket writeData:data withTimeout:3 tag:0];
}

-(void) onSocketDidDisconnect:(AsyncSocket *)sock
{
    NSLog(@"AeroFS: Socket disconnected.");

    if (shouldReconnectIfDisconnected) {
        NSLog(@"Reconnecting...");
        [self reconnect];
    } else {
        [self disconnect];
    }
}

-(BOOL) isConnected
{
    return [socket isConnected];
}

/**
 Read a message from the server
 We should always be waiting for a message, otherwise we won't be notified of disconnections until we try to write
 */
-(void) listenForMessage
{
    [socket readDataToLength:sizeof(uint32_t) withTimeout:NO_TIMEOUT tag:kReadingLength];
}

-(void) onSocket:(AsyncSocket *)sock didConnectToHost:(NSString *)host port:(UInt16)portNumber
{
    NSLog(@"AeroFS: Connection successful on port %u.", portNumber);
    shouldReconnectIfDisconnected = YES;
    [[AeroFinderExt instance] sendGreeting];
}

/**
 Called by AsyncSocket when we received data
 tag is a value from the ReadingState enum that tells in which state we are of the reading process
*/
-(void) onSocket:(AsyncSocket*)sock didReadData:(NSData*)data withTag:(long)tag
{
    uint32_t length;

    //NSLog(@"AeroFS: did read data: %@", data);

    @try {
        switch ((ReadingState) tag) {
        case kReadingLength:
            length = readInt(data);
            if (length == 0 || length > MAX_MESSAGE_LENGTH) {
                NSLog(@"AeroFS: Received an invalid message (length: %u bytes) - disconnecting", length);
                [self disconnect];
                break;
            }
            [socket readDataToLength:length withTimeout:NO_TIMEOUT tag:kReadingData];
            break;

        case kReadingData:
            [[AeroFinderExt instance] parseNotification:[ShellextNotification parseFromData:data]];
            // Wait for the next message
            [self listenForMessage];
            break;
        }
    } @catch (NSException* e) {
        NSLog(@"AeroFS: Exception while trying to parse protobuf data. Disconnecting");
        [self disconnect];
    }
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

@end
