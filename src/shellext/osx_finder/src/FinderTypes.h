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

typedef struct TFENode {
	void* fNodeRef;

    /*
    Sit back, grab a cup of tea, and relax as I tell you the story of the next line of code.
    Up to Mavericks, we were only dealing with pointers to TFENode and everything was easy. However,
    for Mavericks we need to call a method (TListRowView node) that returns a TFENode by value.

    According to the OSX x86-64 calling conventions (http://people.freebsd.org/~obrien/amd64-elf-abi.pdf),
    structs can either be returned in registers or on the stack. Generally speaking, structs smaller
    than or equal to 16 bytes are returned in registers, UNLESS they are non-PODs.... and apparently
    this is the case for TFENode. Even though the size of TFENode is only 8 bytes, the disassembly
    from the Finder shows that the calling convention is to have it passed and returned on the stack.

    (Additional proof that TFENode is a non-POD struct comes from the LLVM code base, where some
    tests contributed by Apple use TFENode as an example and show that it has a constructor.
    See: https://llvm.org/viewvc/llvm-project/cfe/trunk/test/CodeGenObjCXX/2010-08-06-X.Y-syntax.mm?logsort=cvs&revision=138167&view=markup
    The presence of the TIconViewController class in this snippet proves that this is the same
    TFENode as the one we're dealing with, and apparently it has a constructor that takes another
    TFENode)

    But I digress. Point is, we have to make TFENode a non-POD struct in order to make the compiler
    pass and return it on the stack. According to the C++ specification, simply adding a constructor
    would be enough to label this struct as non-POD, but clang has different opinions and won't pass
    it on the stack unless we add a destructor.

    And this, dear reader, is the reason why we have the line below:
    */
    ~TFENode() {}
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

@interface TListRowView : NSTableRowView
@property(nonatomic) TFENode node;
@end

@interface TDimmableIconImageView : NSImageView
@end