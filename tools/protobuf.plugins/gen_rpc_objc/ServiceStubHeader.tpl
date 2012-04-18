@protocol ServiceStubDelegate

- (void)sendBytes:(NSData*)bytes param1:(id)param1 param2:(id)param2;
- (NSError*)decodeError:($ErrorClass$*)error;

@end

@interface $ServiceStubName$ : NSObject {
@private
  id delegate;
}

- (id)initWithDelegate:(id)theDelegate;
- (void)onReplyReceived:(NSData*)reply param1:(id)param1 param2:(id)param2;
