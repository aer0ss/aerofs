#import <Foundation/Foundation.h>

@interface AeroUtils : NSObject

+(BOOL) swizzleClassMethod:(SEL)m1 fromClass:(Class)c1 withMethod:(SEL)m2 fromClass:(Class)c2;
+(BOOL) swizzleInstanceMethod:(SEL)m1 fromClass:(Class)c1 withMethod:(SEL)m2 fromClass:(Class)c2;

@end
