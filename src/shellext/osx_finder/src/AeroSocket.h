#import "AsyncSocket.h"

@class ShellextCall;

@interface AeroSocket : NSObject<AsyncSocketDelegate> {
@private
    AsyncSocket* asyncSocket;
    NSString* sockFile;
}

-(void) connectToServerOnSocket:(NSString*)sockFile;
-(void) sendMessage:(ShellextCall*)call;
-(BOOL) isConnected;
-(void) disconnect;

@end
