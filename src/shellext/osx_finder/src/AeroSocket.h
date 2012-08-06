#import "AsyncSocket.h"

@class ShellextCall;

@interface AeroSocket : NSObject<AsyncSocketDelegate> {
@private
    AsyncSocket* socket;
    UInt16 port;
}

-(void) connectToServerOnPort:(UInt16)port;
-(void) sendMessage:(ShellextCall*)call;
-(BOOL) isConnected;
-(void) disconnect;

@end
