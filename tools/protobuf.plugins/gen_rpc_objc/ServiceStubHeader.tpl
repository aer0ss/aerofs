@protocol ServiceStubDelegate

- (void) sendBytes:(NSData*)bytes withSelector:(SEL)selector andObject:(id)object;

@end

@interface $ServiceStubName$ : NSObject {
@private
  id delegate;
}

- (id) initWithDelegate:(id)theDelegate;
- (void) onReplyReceived:(NSData*)reply withSelector:(SEL)selector andObject:(id)object;
