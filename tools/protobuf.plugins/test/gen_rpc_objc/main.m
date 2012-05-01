#import "out/Address_book.pb.h"
#import "out/Rpc_service.pb.h"

#define ADD_PERSON_TYPE 1
#define ADD_PEOPLE_TYPE 2

////////////////
// Server
////////////////
@interface Server : NSObject

- (NSData*) processRpc:(NSData*)data;

@end

@implementation Server

- (NSData*) processRpc:(NSData*)data
{
    Payload* payloadReply;
    Payload* payload = [Payload parseFromData:data];
    switch ([payload type]) {
        case ADD_PERSON_TYPE: {
            AddPersonCall* call = [AddPersonCall parseFromData:[payload payloadData]];
            if (call.person.name.length > 1) {
                AddPersonReply* reply = [[[AddPersonReply builder] setId:1234] build];
                payloadReply = [[[[Payload builder] setType: ADD_PERSON_TYPE] setPayloadData:[reply data]] build];
            } else {
                // Tried to add an empty person, fail
                ErrorReply* reply = [[[ErrorReply builder] setErrorMessage:@"Cannot add an empty person"] build];
                payloadReply = [[[[Payload builder] setType:0] setPayloadData:[reply data]] build];
            }
            break;
        }
        case ADD_PEOPLE_TYPE: {
            AddPeopleCall* call = [AddPeopleCall parseFromData:[payload payloadData]];
            NSMutableArray* arr = [NSMutableArray arrayWithCapacity:call.people.count];
            for (int i = 0; i < call.people.count; i++) {
                Person* person = [call.people objectAtIndex:i];
                [arr addObject: [NSNumber numberWithInteger:person.name.length]];
            }
            AddPeopleReply* reply = [[[AddPeopleReply builder] setLengthNameArray:arr] build];
            payloadReply = [[[[Payload builder] setType: ADD_PEOPLE_TYPE] setPayloadData:[reply data]] build];
            break;
        }
        default:
            NSAssert(NO, @"Server: client payload has the wrong type");
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
- (void)sendBytes:(NSData*)bytes param1:(id)param1 param2:(id)param2;
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

-(void)sendBytes:(NSData*)bytes param1:(id)param1 param2:(id)param2
{
    NSData* data = [server processRpc:bytes];
    [stub onReplyReceived:data param1:param1 param2:param2];
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
    [stub addPerson: person withSomeValue:nil andPerform:@selector(onPersonAdded:error:) withObject:self];
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

- (void)shouldAddAPersonUsingBlocks
{
    Person* person = [[[[Person builder] setName:@"Joe Foo"] setEmail:@"joe@foo.com"] build];
    [stub addPerson:person withSomeValue:@"some value" usingBlock:^(AddPersonReply* reply, NSError* error) {
        NSAssert(error == nil, @"adding a person returned an error");
        NSAssert(reply.id == 1234, @"Didn't receive the expected id after calling addPerson");
    }];
}

- (void)shouldWorkWithRepeatedFields
{
    Person* p1 = [[[Person builder] setName:@"John"] build];
    Person* p2 = [[[Person builder] setName:@"Antonio"] build];
    NSArray* arr = [NSArray arrayWithObjects:p1, p2, nil];
    [stub addPeople:arr withTestValues:nil usingBlock:^(AddPeopleReply* reply, NSError *error) {
        NSAssert(error == nil, @"adding people returned an error");
        NSAssert(reply.lengthName.count > 0, @"adding people failed");
        NSAssert([reply.lengthName int32AtIndex:0] == p1.name.length, @"adding people failed");
        NSAssert([reply.lengthName int32AtIndex:1] == p2.name.length, @"adding people failed");
    }];
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
    [client shouldAddAPersonUsingBlocks];
    [client shouldWorkWithRepeatedFields];

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

