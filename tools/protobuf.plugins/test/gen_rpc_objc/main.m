#import "out/Address_book.pb.h"
#import "out/Rpc_service.pb.h"

#define ADD_PERSON_TYPE 1

////////////////
// Server
////////////////
@interface Server : NSObject

- (NSData*) processRpc:(NSData*)data;

@end

@implementation Server

- (NSData*) processRpc:(NSData*)data
{
    Payload* payload = [Payload parseFromData:data];
    NSAssert([payload type] == ADD_PERSON_TYPE, @"Server: client payload has the wrong type");
    AddPersonCall* call = [AddPersonCall parseFromData:[payload payloadData]];

    Payload* payloadReply;
    if ([[[call person] name] length] > 1) {
        AddPersonReply* reply = [[[AddPersonReply builder] setId:1234] build];
        payloadReply = [[[[Payload builder] setType: ADD_PERSON_TYPE] setPayloadData:[reply data]] build];
    } else {
        // Tried to add an empty person, fail
        ErrorReply* reply = [[[ErrorReply builder] setErrorMessage:@"Cannot add an empty person"] build];
        payloadReply = [[[[Payload builder] setType:0] setPayloadData:[reply data]] build];
    }

    return [payloadReply data];
}

@end

////////////////
// Client
////////////////

@interface Client : NSObject<ServiceStubDelegate> {
    Server* server;
    AddressBookServiceStub* stub;
    BOOL lastTestFinished;
}
@property (readonly) BOOL lastTestFinished;

- (id)initWithServer:(Server*)s;
- (void)sendBytes:(NSData*)bytes withSelector:(SEL)selector andObject:(id)object;
- (NSError*)decodeError:(ErrorReply*) error;

/// testing methods
- (void)shouldAddAPerson;
- (void)shouldFailEmptyPerson;

@end

@implementation Client
@synthesize lastTestFinished;

- (id)initWithServer:(Server*)s
{
    self = [super init];
    server = s;
    stub = [[AddressBookServiceStub alloc] initWithDelegate:self];
    lastTestFinished = NO;
    return self;
}

-(void)sendBytes:(NSData*)bytes withSelector:(SEL)selector andObject:(id)object
{
    NSData* data = [server processRpc:bytes];
    [stub onReplyReceived:data withSelector:selector andObject:object];
}

- (NSError*)decodeError:(ErrorReply*)error
{
    //[error errorMessage]
    NSError* nserror = [NSError errorWithDomain:@"localNak" code:0 userInfo:nil];
    return nserror;
}

- (void)shouldAddAPerson
{
    Person* person = [[[[Person builder] setName:@"Joe Foo"] setEmail:@"joe@foo.com"] build];
    [stub addPerson: person withSomeValue:@"some value" andPerform:@selector(onPersonAdded:error:) withObject:self];
}

-(void)onPersonAdded:(AddPersonReply*)reply error:(NSError*)error
{
    NSAssert(error == nil, @"adding a person returned an error");
    NSAssert(reply.id == 1234, @"Didn't receive the expected id after calling addPerson");
}

- (void)shouldFailEmptyPerson
{
    Person* person = [[[Person builder] setName:@""] build];
    [stub addPerson: person withSomeValue:@"" andPerform:@selector(onEmptyPersonAdded:error:) withObject:self];
}

- (void)onEmptyPersonAdded:(AddPersonReply*)reply error:(NSError*)error
{
    NSAssert(error != nil, @"Adding an empty person did NOT fail");
    lastTestFinished = YES;
}

@end

int main (int argc, const char * argv[])
{
    NSAutoreleasePool * pool = [[NSAutoreleasePool alloc] init];

    // Start the test
    Server* server = [[Server alloc] init];
    Client* client = [[Client alloc] initWithServer: server];

    [client shouldAddAPerson];
    [client shouldFailEmptyPerson];

    // Enter the run loop, and give it 2 seconds to get the reply
    NSRunLoop *runLoop = [NSRunLoop currentRunLoop];

    [runLoop runMode:NSDefaultRunLoopMode beforeDate:[NSDate dateWithTimeIntervalSinceNow:2]];

    [pool release];

    if (client.lastTestFinished == YES) {
        NSLog(@"Test successful");
        return 0;
    } else {
        NSLog(@"Test failed");
        return 1;
    }
}

