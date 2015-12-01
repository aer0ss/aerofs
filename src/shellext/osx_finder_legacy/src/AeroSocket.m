#import "AeroSocket.h"
#import "../gen/Shellext.pb.h"
#import "AeroFinderExt.h"
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
-(void) onSocketDidDisconnect:(AsyncSocket *)sock;
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
    sockFile = theSockFile;
    [self reconnect];
}

-(void) reconnect
{
    [self disconnect];
    asyncSocket = [[AsyncSocket alloc] initWithDelegate:self];

    // struct to specify unix domain socket as socket type and socket file.
    struct sockaddr_un local;
    local.sun_family = AF_UNIX;
    strcpy(local.sun_path, [sockFile fileSystemRepresentation]);
    local.sun_len =  SUN_LEN(&local);
    NSData *addrData = [NSData dataWithBytes:&local length:sizeof(struct sockaddr_un)];
    [asyncSocket connectToAddress:addrData withTimeout:DEFAULT_TIMEOUT error:nil];

    [self listenForMessage];
}

/**
 Disconnects and releases the AsyncSocket
 Make sure we won't automatically try reconnect
*/
-(void) disconnect
{
    shouldReconnectIfDisconnected = NO;
    [asyncSocket setDelegate:nil];
    [asyncSocket disconnect];
    asyncSocket = nil;
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

-(void) onSocketDidConnect:(AsyncSocket *)sock
{
    NSLog(@"AeroFS: Connection successful on socket file: %@.", sockFile);
    shouldReconnectIfDisconnected = YES;
    [[AeroFinderExt instance] sendGreeting];
}

/**
 Called by AsyncSocket when we received data
 tag is a value from the ReadingState enum that tells in which state we are of the reading process
*/
-(void) onSocket:(AsyncSocket *)sock didReadData:(NSData*)data withTag:(long)tag
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
            [asyncSocket readDataToLength:length withTimeout:NO_TIMEOUT tag:kReadingData];
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
