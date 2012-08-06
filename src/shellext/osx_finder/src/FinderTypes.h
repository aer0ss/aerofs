/**
 This file defines several types and classes from the Finder that we must use.

 Finder classes (starting with T or F) are defined as categories to the closest public
 Cocoa class.

 Most of those definitions are just needed to make everything compile smoothly, without
 warnings or errors.

 Files including this header must be compiled as Objective-C++ (ie: have a .mm extension)
 because the Finder uses C++'s std::vector.
*/

#import <Foundation/Foundation.h>
#include <vector>

// FENode and FINode stuff:

typedef struct {
	void* fNodeRef;
} TFENode;

typedef std::vector<TFENode> TFENodeVector;

id FINodeFromFENode(const TFENode* node);

@interface NSObject (FINode)
- (NSURL*)previewItemURL;
@end

@interface NSArray (NSArrayAdditions)
+ (id)FINodesFromFENodeVector:(const TFENodeVector *)arg1;
@end

// ViewController methods

@interface NSCell(TListViewIconAndTextCell)
-(const TFENode*)node;
@end

@interface NSViewController(TBrowserViewController)
- (void)getNodes:(TFENodeVector*)arg1 fromIndexSet:(NSIndexSet*)arg2 upTo:(unsigned long long)arg3;
- (NSView*) browserView;
@end

@interface NSViewController(TColumnViewController)
- (void)getNodes:(TFENodeVector*)arg1 fromSet:(NSIndexSet*)arg2 forColumn:(long long)arg3 upTo:(unsigned long long)arg4;
@end

@interface NSViewController(TIconViewController)
- (NSIndexSet*) selectedItems;
@end
