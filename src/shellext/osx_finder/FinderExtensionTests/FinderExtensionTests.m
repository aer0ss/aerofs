#import <SenTestingKit/SenTestingKit.h>

#import "AeroNode.h"

@interface FinderExtensionTests : SenTestCase
@end

NSString* printChild(AeroNode* node);

@implementation FinderExtensionTests

- (void)setUp
{
    [super setUp];
    // Set-up code here.
}

- (void)tearDown
{
    // Tear-down code here.
    [super tearDown];
}

/**
 Convenience function to recursively print the node tree
 Use only for debugging when writing a test
*/
NSString* printChild(AeroNode* node)
{
    NSMutableString* r = [NSMutableString string];
    for(NSString* c in node.children) {
        [r appendFormat:@"%@:%@, ", c, printChild([node.children objectForKey:c])];
    }
    
    return [NSString stringWithFormat:@"%ld(%@)", node.children.count, r];
}

////////////////////////////////////////////////////////////////////////////
//
//                 Beginning of tests
//
////////////////////////////////////////////////////////////////////////////

- (void) testNodesAreReleasedWhenSettingToNoStatus
{
    AeroNode* root = [[AeroNode alloc] initWithName:@"/" andParent:nil];
    AeroNode* bar = [root getNodeAtPath:@"/foo/bar" createPath:YES];
    bar.status = Downloading;
    AeroNode* baz = [root getNodeAtPath:@"/foo/baz" createPath:YES];
    baz.status = Uploading;
    AeroNode* foo = [root getNodeAtPath:@"/foo" createPath:NO];
    
    STAssertEquals(root.children.count, (NSUInteger) 1, @"root should have one child: foo");
    STAssertEquals(foo.children.count,  (NSUInteger) 2, @"foo should have 2 children: bar and baz");
    
    bar.status = NoStatus; // release bar
    STAssertEquals(root.children.count, (NSUInteger) 1, @"root should have one child: foo");
    STAssertEquals(foo.children.count,  (NSUInteger) 1, @"foo should have one child: baz");
    
    baz.status = NoStatus; // release baz, thus releasing foo
    STAssertEquals(root.children.count, (NSUInteger) 0, @"baz and foo should have been released");

    [root release];
}

@end
