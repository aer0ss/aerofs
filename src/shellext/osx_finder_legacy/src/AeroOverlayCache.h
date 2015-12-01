//
// Created by hugues on 10/19/12.
//
//

#import <Foundation/Foundation.h>

// Use pimpl to prevent the C++ cache from leaking all around the Objective-C code
struct AeroOverlayCachePrivate;

@protocol AeroEvictionDelegate
- (void)evicted:(NSString*)path withValue:(int)value;
@end;

/**
 * Simple Obj-C wrapper around OverlayCache with an interface close to NSCache (but storing integers instead of object
 * and with slightly different method names to reflect specialization)
 */
@interface AeroOverlayCache : NSObject {
@private
    struct AeroOverlayCachePrivate* d;
}

- (id)init;
- (id)initWithLimit:(int)limit;

- (int)countLimit;
- (void)setCountLimit:(int)limit;

- (void)clear;
- (int)overlayForPath:(NSString*)path;
- (int)overlayForPath:(NSString*)path withDefault:(int)defaultValue;
- (void)setOverlay:(int)value forPath:(NSString*)path;

- (void)setDelegate:(id<AeroEvictionDelegate>)delegate;

@end