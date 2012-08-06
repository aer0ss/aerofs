#import <objc/runtime.h>

#import "AeroContextMenu.h"
#import "AeroFinderExt.h"
#import "FinderTypes.h"
#import "AeroUtils.h"

// private functions
NSString* getTargetPathFromViewController(id viewController);
NSURL* targetInColumnVC(id columnVC);
NSURL* targetInListVC(id listVC);
NSURL* targetInIconVC(id iconVC);
NSURL* urlOfFirstNodeInVector(TFENodeVector* nodeVec);

@implementation AeroContextMenu

-(id)init
{
    self = [super init];
    if (!self) {
        return nil;
    }

    [AeroUtils swizzleClassMethod:@selector(addViewSpecificStuffToMenu:browserViewController:context:) fromClass:NSClassFromString(@"TContextMenu")
                       withMethod:@selector(aero_addViewSpecificStuffToMenu:browserViewController:context:) fromClass:[AeroContextMenuSwizzledMethods class]];

    return self;
}

@end

@implementation AeroContextMenuSwizzledMethods

+(void) aero_addViewSpecificStuffToMenu:(NSMenu*)menu browserViewController:(id)browserVC context:(unsigned int)ctx
{
    @try {
        // Call the original method
        [self aero_addViewSpecificStuffToMenu:menu browserViewController:browserVC context:ctx];

        if(![[AeroFinderExt instance] shouldModifyFinder]) {
            return;
        }

        NSString* path = getTargetPathFromViewController(browserVC);

        if (path == nil) { // User right-clicked on blank space (ie: not on a file or directory)
            return;
        }

        if([[AeroFinderExt instance] shouldDisplayContextMenu:path]) {

            NSMenuItem* item = [menu insertItemWithTitle:@"AeroFS" action:nil keyEquivalent:@"" atIndex:2];

            NSMenu* submenu = [[[NSMenu alloc] init] autorelease];

            NSMenuItem* share = [submenu insertItemWithTitle:NSLocalizedString(@"Share Folder...", @"Context menu")
                                                      action:@selector(showShareFolderDialog:)
                                               keyEquivalent:@""
                                                     atIndex:0];

            [share setTarget: [AeroFinderExt instance]];
            [share setRepresentedObject:path];
            [item setSubmenu: submenu];
        }
    } @catch (NSException* exception) {
        NSLog(@"AeroFS: Exception in aero_addViewSpecificStuffToMenu: %@", exception);
    }
}

/**
  Determine the kind of view controller class, and calls the appropriate targetIn* function.
  Returns the path to the file the user right-clicked on, or nil in the following cases:
    - the user right-clicked on empty space (no target)
    - viewController it's not an instance of a known view controller class.
*/
NSString* getTargetPathFromViewController(id viewController)
{
    NSURL* targetUrl;

    if ([viewController isKindOfClass:NSClassFromString(@"TColumnViewController")]) {
        targetUrl = targetInColumnVC(viewController);
    } else if([viewController isKindOfClass:NSClassFromString(@"TListViewController")]) {
        targetUrl = targetInListVC(viewController);
    } else if([viewController isKindOfClass:NSClassFromString(@"TIconViewController")]) {
        targetUrl = targetInIconVC(viewController);
    } else {
        NSLog(@"AeroFS: Unknown ViewController class: %s", class_getName([viewController class]));
        return nil;
    }

    return [targetUrl path];
}

/**
	Returns the URL for the file that the user right-clicked on, in column-view mode only
*/
NSURL* targetInColumnVC(id columnVC)
{
    //Note: use [columnVC selectedItemsForColumn: clickedCol] to get an IndexSet of the selected items

    @try {
        id view = [columnVC browserView];
        long clickedCol = [view clickedColumn];
        long clickedRow = [view clickedRow];

        if (clickedCol < 0 || clickedRow < 0) {
            return nil;
        }

        TFENodeVector nodeVec;
        [columnVC getNodes:&nodeVec fromSet:[NSIndexSet indexSetWithIndex:clickedRow] forColumn:clickedCol upTo:1];

        return urlOfFirstNodeInVector(&nodeVec);

    } @catch (NSException* exception) {
        NSLog(@"AeroFS: exception in targetInColumnVC: %@", exception);
    }
    return nil;
}

NSURL* targetInListVC(id listVC)
{
	@try {
        id view = [listVC browserView];
        long clickedRow = [view clickedRow];

        if (clickedRow < 0) {
            return nil;
        }

        TFENodeVector nodeVec;
        [listVC getNodes:&nodeVec fromIndexSet:[NSIndexSet indexSetWithIndex:clickedRow] upTo:1];

        return urlOfFirstNodeInVector(&nodeVec);

    } @catch (NSException* exception) {
        NSLog(@"AeroFS: exception in targetInListVC: %@", exception);
    }
    return nil;
}

NSURL* targetInIconVC(id iconVC)
{
    @try {
        NSIndexSet* selectedItems = [iconVC selectedItems];

        if ([selectedItems count] != 1) {
            return nil;
        }

        TFENodeVector nodeVec;
        [iconVC getNodes:&nodeVec fromIndexSet:selectedItems upTo:1];

        return urlOfFirstNodeInVector(&nodeVec);

    } @catch (NSException* exception) {
        NSLog(@"AeroFS: exception in targetInIconVC: %@", exception);
    }
    return nil;
}

/**
 Convenience function to return the URL of the first node in a given TFENodeVector
*/
NSURL* urlOfFirstNodeInVector(TFENodeVector* nodeVec)
{
    NSArray* finodes = [NSArray FINodesFromFENodeVector: nodeVec];
    if ([finodes count] >= 1) {
        id target = [finodes objectAtIndex:0];
        return [target previewItemURL];
    }
    return nil;
}

@end
