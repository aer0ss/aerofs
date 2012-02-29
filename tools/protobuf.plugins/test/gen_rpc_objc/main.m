#import <Foundation/Foundation.h>
#import "out/Address_book.pb.h"

@interface TestClass : NSObject<ServiceStubDelegate> {
    AddressBookServiceStub* service;
    BOOL success;
}

-(void) startTest;
-(void) onReplyReceived:(int)id;
-(BOOL) successful;
@end

@implementation TestClass

- (id)init
{
    self = [super init];
    if (self) {
        service = [[AddressBookServiceStub alloc] initWithDelegate:self];
    }
    return self;
}

-(void) startTest
{
    success = NO;
    Person* person = [[[[Person builder] setName:@"Joe Foo"] setEmail:@"joe@foo.com"] build];
    [service addPerson: person withSomeValue:@"test" andPerform:@selector(onReplyReceived:) withObject:self];
}

-(void) onReplyReceived:(int)id
{
    NSAssert(id == 1234, @"Didn't receive the expected id after calling addPerson");
    success = YES;
}

// TODO: make property
-(BOOL) successful { return success; }

-(void)sendBytes:(NSData*)bytes withSelector:(SEL)selector andObject:(id)object
{
    AddPersonReply* reply = [[[AddPersonReply builder] setId:1234] build];
    Payload* payload = [[[[Payload builder] setType:0] setPayloadData:[reply data]] build];
    [service onReplyReceived:[payload data] withSelector:selector andObject:object];
}

@end

int main (int argc, const char * argv[])
{
    NSAutoreleasePool * pool = [[NSAutoreleasePool alloc] init];
        
    // Start the test
    TestClass* test = [[TestClass alloc] init];
    [test startTest];

    // Enter the run loop, and give it 2 seconds to get the reply
    NSRunLoop *runLoop = [NSRunLoop currentRunLoop];    
    
    [runLoop runMode:NSDefaultRunLoopMode beforeDate:[NSDate dateWithTimeIntervalSinceNow:2]];
        
    [pool release];
    
    if (test.successful == YES) {
        NSLog(@"Test successful");    
        return 0;
    } else {
        NSLog(@"Test failed");
        return 1;
    }
}

