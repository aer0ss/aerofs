#import <CocoaAsyncSocket/GCDAsyncSocket.h>

@class ShellextCall;

@interface AeroSocket : NSObject<GCDAsyncSocketDelegate> {
@private
    GCDAsyncSocket* asyncSocket;
    NSString* sockFile;
}

-(void) connectToServerOnSocket:(NSString*)sockFile;
-(void) sendMessage:(ShellextCall*)call;
-(BOOL) isConnected;
-(void) disconnect;

@end
