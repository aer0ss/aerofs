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

- (void) onReplyReceived:(NSData*)data param1:(id)param1 param2:(id)param2;
{
  id reply = nil;
  NSError* error = nil;
  Payload* payload = [Payload parseFromData: data];

  switch ((CallType) [payload type]) {
