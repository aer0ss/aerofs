@implementation $ServiceStubName$

typedef enum {
  $EnumRpcTypes$
} CallType;

- (id) initWithDelegate:(id)theDelegate;
{
  self = [super init];
  if (self) {
    delegate = theDelegate;
  }
  return self;
}

- (void) onReplyReceived:(NSData*)data withSelector:(SEL)selector andObject:(id)target;
{   
  NSMethodSignature* signature = [target methodSignatureForSelector:selector];

  // TODO: Assert that signature is not nil (ie: that the target does respond to that selector);

  NSInvocation* invocation = [NSInvocation invocationWithMethodSignature:signature];
  [invocation setTarget:target];
  [invocation setSelector:selector];

  Payload* payload = [Payload parseFromData: data];
  switch ((CallType) [payload type]) {
