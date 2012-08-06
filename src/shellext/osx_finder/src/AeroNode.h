#import <Foundation/Foundation.h>

typedef enum {
    NoStatus                    = 0,
    Downloading                 = 1 << 0,
    Uploading                   = 1 << 1
} NodeStatus;

@interface AeroNode : NSObject {
    NSString* name;
    AeroNode* parent;
    NSMutableDictionary* children;
    NodeStatus ownStatus;
    NodeStatus childrenStatus;
}

@property (readonly) NSString* name;
@property (readonly) AeroNode* parent;
@property (readonly) NSMutableDictionary* children;
@property (readonly) NodeStatus ownStatus;
@property (readonly) NodeStatus childrenStatus;
@property (readwrite) NodeStatus status;

-(id)initWithName:(NSString*)name andParent:(AeroNode*)theParent;
-(AeroNode*) getNodeAtPath:(NSString*)path createPath:(BOOL) create;

@end
