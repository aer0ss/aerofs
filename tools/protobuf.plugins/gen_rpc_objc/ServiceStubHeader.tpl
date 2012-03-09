@protocol ServiceStubDelegate

- (void)sendBytes:(NSData*)bytes withSelector:(SEL)selector andObject:(id)object;
- (NSError*)decodeError:($ErrorClass$*)error;

@end

@interface $ServiceStubName$ : NSObject {
@private
  id delegate;
}

- (id)initWithDelegate:(id)theDelegate;
- (void)onReplyReceived:(NSData*)reply withSelector:(SEL)selector andObject:(id)object;
