#import "FinderTypes.h"

id FINodeFromFENode(const TFENode* node)
{
    // [NSArray FINodesFromFENodeVector] dereferences node->fNodeRef and expects it to be non-nil
    if (!node || !node->fNodeRef) return nil;

    TFENodeVector nodeVec;
    nodeVec.push_back(*node);

    NSArray* arr = [NSArray FINodesFromFENodeVector: &nodeVec];
    if ([arr count] == 1) {
        return [arr objectAtIndex: 0];
    } else {
        return nil;
    }
}
