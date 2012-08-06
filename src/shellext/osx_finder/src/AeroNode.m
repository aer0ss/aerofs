#import "AeroNode.h"

@interface AeroNode (Private)
-(void)updateChildrenStatus;
-(AeroNode*)nodeAtPath:(NSArray*)pathComponents arrayIndex:(int)index createPath:(BOOL)create;
@end

@implementation AeroNode

@synthesize name;
@synthesize parent;
@synthesize children;
@synthesize ownStatus;
@synthesize childrenStatus;

-(void)dealloc
{
    [name release];
    [children release]; // will release all sub-nodes
    [super dealloc];
}

- (id)init
{
    // make it impossible to call init by calling initWithName with an empty name
    return [self initWithName:@"" andParent:nil];
}

-(id)initWithName:(NSString*)theName andParent:(AeroNode*)theParent
{
    self = [super init];
    if (self) {

        // Do not create Nodes with empty names
        if (theName == nil || theName.length == 0) {
            [self release];
            [super doesNotRecognizeSelector:_cmd];
            return nil;
        }

        children = [[NSMutableDictionary alloc] init];
        name = [theName retain];
        parent = theParent;
        ownStatus = NoStatus;
        childrenStatus = NoStatus;
    }

    return self;
}

-(NodeStatus)status
{
    return ownStatus | childrenStatus;
}

-(void)setStatus:(NodeStatus)theStatus
{
    ownStatus = theStatus;
    [parent updateChildrenStatus];
}

/**
 Update the node's childrenStatus field with the status of all children
 If the status has changed, recursively call the parent's updateChildrenStatus
 */
-(void) updateChildrenStatus
{
    NodeStatus oldStatus = self.status;

    childrenStatus = NoStatus;
    NSEnumerator *enumerator = [children objectEnumerator];
    NSMutableArray* childrenToDelete = [NSMutableArray arrayWithCapacity:[children count]];
    AeroNode* child;
    while ((child = [enumerator nextObject])) {
        NodeStatus st = child.status;
        childrenStatus |= st;
        if (st == NoStatus) {
            [childrenToDelete addObject:child.name];
        }
    }

    [children removeObjectsForKeys:childrenToDelete];

    if (self.status != oldStatus) {
        [parent updateChildrenStatus];
    }
}

/**
 Returns a pointer to a AeroNode at the given path.
 If create is set to YES, creates the intermediary nodes if they don't exist,
 (similarly to mkdir -p), otherwise, returns nil.
 */
-(AeroNode*) getNodeAtPath:(NSString*)path createPath:(BOOL) create
{
    NSString* root = self.name;

    if (![path hasPrefix:root]) {
        return nil; // path is not under root
    }

    if (path.length == root.length) {
        return self; // path and root are the same
    }

    if ([path characterAtIndex:root.length] != '/') {
        return nil; // ensure path is indeed under root (there's a path separator)
    }

    // Remove root from the path and get an array with the other path components
    path = [path substringFromIndex:root.length + 1]; // +1 to remove the path separator
    NSArray* pathComponents = [path pathComponents];

    if (pathComponents.count == 0) {
        return self;
    }

    return [self nodeAtPath:pathComponents arrayIndex:0 createPath:create];
}

-(AeroNode*) nodeAtPath:(NSArray*)pathComponents arrayIndex:(int)index createPath:(BOOL)create
{
    if (index == [pathComponents count]) {
        return self;
    }

    NSString* component = [pathComponents objectAtIndex:index];
    AeroNode* child = [children objectForKey:component];

    if (child == nil) {
        if (!create) {
            return nil;
        }
        child = [[AeroNode alloc] initWithName:component andParent:self];
        [children setObject:child forKey:component];
        [child release];
    }

    index++;
    return [child nodeAtPath:pathComponents arrayIndex:index createPath:create];
}

@end
