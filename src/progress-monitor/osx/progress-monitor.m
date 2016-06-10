//
//  progress-monitor.m
//
//  Created by Jeffrey Miller on 6/8/16.
//  Copyright © 2016 Air Computing. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>
#import <Cocoa/Cocoa.h>

// Helper methods
@interface Helpers : NSObject
+(NSImage*)icon;
@end

// An app delegate that allows closing the window to close the application
@interface ListeningProgressIndicator : NSProgressIndicator <NSApplicationDelegate>
- (BOOL)applicationDidFinishLaunching:(NSNotification*)aNotification;
@end

// Entry point for the application
int main(int argc, const char * argv[]) {
    @autoreleasepool {
        // The accessory activation policy prevents a dock icon and menu bar from being instantiated
        // for the process. The process will also not show up in the Force Quit list. Any quitting
        // would need to be handled by the updater or using activity monitor / terminal.
        NSApplication *thisApp = [NSApplication sharedApplication];
        [thisApp setActivationPolicy:NSApplicationActivationPolicyAccessory];

        // verify sane arguments
        NSArray<NSString*> *arguments = [[NSProcessInfo processInfo] arguments];
        NSInteger manifestCount;
        @try {
            manifestCount = [[arguments objectAtIndex:1] intValue];
            if (manifestCount < 1) {
                [thisApp terminate:nil];
            }
        }
        @catch (NSException *exception) {
            [thisApp terminate:nil];
        }

        // Set up the window
        NSInteger const WINDOW_WIDTH  = 300;
        NSInteger const WINDOW_HEIGHT = 110;
        NSInteger const PADDING       = 20;
        NSInteger const ICON_SIZE     = 70;

        NSWindow *window = [[NSWindow alloc] initWithContentRect:NSMakeRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT)
                                                       styleMask:NSTitledWindowMask
                                                         backing:NSBackingStoreBuffered
                                                           defer:NO];
        [window cascadeTopLeftFromPoint:NSMakePoint(NSWidth([[window screen] frame]) / 2 - WINDOW_WIDTH,
                                                    NSHeight([[window screen] frame]) / 2  + WINDOW_HEIGHT)];
        [window setTitle:@"AeroFS"];

        // Add a Text Field for describing what is going on
        NSTextField *description = [[NSTextField alloc] initWithFrame:CGRectMake(2 * PADDING+ICON_SIZE,
                                                                                 2 * PADDING,
                                                                                 WINDOW_WIDTH - (3 * PADDING + ICON_SIZE),
                                                                                 WINDOW_HEIGHT - 3 * PADDING)];
        [description setStringValue:@"AeroFS is upgrading to the latest version."];
        [description setEditable:NO];
        [description setBordered:NO];
        [description setBackgroundColor:[window backgroundColor]];

        // Load the base64 encoded image
        NSImage *image = [Helpers icon];

        // Add the icon
        NSImageView *imageView = [[NSImageView alloc] initWithFrame:CGRectMake(PADDING, PADDING, ICON_SIZE, ICON_SIZE)];
        [imageView setImage:image];

        // Add a progress bar
        ListeningProgressIndicator *progress = [[ListeningProgressIndicator alloc] initWithFrame:CGRectMake(2 * PADDING + ICON_SIZE,
                                                                                                            PADDING,
                                                                                                            WINDOW_WIDTH - (3 * PADDING + ICON_SIZE),
                                                                                                            PADDING)];
        NSInteger initialProgress = 0;
        [progress setMinValue:initialProgress];
        [progress setDoubleValue:initialProgress];
        [progress setMaxValue:manifestCount];
        [progress setStyle:NSProgressIndicatorBarStyle];
        [progress setIndeterminate:NO];
        [progress setUsesThreadedAnimation:YES];

        // for simplicity, the progress bar is the app delegate, it is the only thing that does anything
        [thisApp setDelegate:progress];

        // build and present the window
        [[window contentView] addSubview:description];
        [[window contentView] addSubview:progress];
        [[window contentView] addSubview:imageView];
        [window makeKeyAndOrderFront:nil];

        // Now that everything is set up, dispatch a run loop for the application
        // This must be the last thing done on main - all other logic needs to be dispatched out onto
        // other threads if it needs to happen.
        [thisApp activateIgnoringOtherApps:YES];
        [thisApp run];
    }
    return 0;
}

// The prgoress bar will function as the app delegate because it is the only
// component of the application that responds to any input
@implementation ListeningProgressIndicator : NSProgressIndicator

// Prepare to receive data on stdin and subscribe to notifications
-(BOOL)applicationDidFinishLaunching:(NSNotification*)aNotification {
    NSFileHandle *stdin = [NSFileHandle fileHandleWithStandardInput];
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(stdinDataAvailable)
                                                 name:NSFileHandleDataAvailableNotification
                                               object:stdin];
    [stdin waitForDataInBackgroundAndNotify];
    return YES;
}

// Handle data available on stdin
-(void)stdinDataAvailable {
    // Read data and terminate if it doesn't make sense
    NSFileHandle *stdin = [NSFileHandle fileHandleWithStandardInput];
    NSData *data = [stdin availableData];
    if (!data || [data length] == 0){
        [NSApp terminate:nil];
    }
    NSString *stringData = [[NSString alloc] initWithData:data
                                                 encoding:NSUTF8StringEncoding];
    if(!stringData) {
        [NSApp terminate:nil];
    }
    double progress = [stringData doubleValue];
    [self setDoubleValue:progress];
    [stdin waitForDataInBackgroundAndNotify];
}

@end

@implementation Helpers

// Unpacks the icon (stored as base64 encoded string for easy delivery)
+(NSImage*)icon{
    NSString *base64ImageString = @"iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAAAAXNSR0IArs4c6QAAG/9JREFUeAHtXQl4VcX1n5l733tJ2DcRkMUECEkQawElJNYgJGEREDDoVxHRKi5t/buUWm1t8V+tdasVP/+1IgqI1SYiskhWlLKGraCQAIEELEIBWUKALO/dO/P/3WAi7/Hylru89xK535fv5c7MWWbOuTNnzpyZoeTyE3ALzJ6dJl9/s9rfZnMNVFVpIJPoVYLzHoTQHpSRrvi1EcLb4pdqSIUgdUivISo5RYk4zCmt4IIelCgpOV+nbJk0ovhgwMQtKljPqEW4mz3a7Oy01h271aVyWaQJIqVBpNdRQu0mVuwElGQj47zgnEusCIdCXFYAD2lm5yV3bNOaTJJkKYsIfvOFr9qjkEWvQog9gtMcTmsXjE3ZVm4RGTe0lxUAzTFbEJa6LjmDy/QhIuhYQons1kpheKFcrFU5e6eq4+mPpiaVOK1i4QetAAu/zGh1RU31g4TzhxilcVY1siG8QhyG/fDX6m/Z3ydOXH/WEC4vwD9IBcjOTo7u0J08oErsSUbIlV7aJQKT6Bkq1Od2b23/xiOP5NaZxeAPSgFgcNGCjan3CCqebz6Cdxe1IOIgI/zJ9OTibPccfW8/GAVYvin1Wrsq3sQELUVfU0UalFgpnPKDmTetOWSEsxavANklSfY2p9s/yxiZBeFLRhrrAqxwEcKqMck/IwiJJlQ4MDXE3D/0DxXiLGYqT6SnFM/VS71FK8DKNTcmSnb1Ayroj4JqIEoqiSA7VC52yoTvJJK8u5a4jkXJyvH0IdvOeOLaunWw7fCpqC7RbdjVKnH1lQW9VlA2FEPOUCidw7O82e+ciMWSre5n3njzR6vFKkBh8fB7OKd/C0QAGFedhJPVmA1+rgqx6vyR7tunTs1R/TWev/z33kuL6tLPlexgfDyn7FY09tX+YPTmUyr2VlfTiRNvXr83GBwtTgG0Lr/d2fZzqCAP+GoIfJ0qZbRIVdQcOcr5sZ6vxxd+z7wLBujQZEod96HbvgPe4mjPMsbf6RmXk08Yd9OGNYHialEKsHxrWmeb4lzGBE1usgGEOEIpf9spRf193PWrjzZZzsKMFWtTOziY8rBg0qMg09lMUlpvxlWaNSZ1/bJA8LYYBSjakhrLFZGLSvf3VnFM/XYwwV/oYHMtGTJkGwy58D/aWkObHsqjjPFZ5hqSwqWq9LZAlKBFKEDhv5Kv4TZaRCm9wlOsmuAJl57NSF67FPYADPfIe5YWD+warbZ5mTB2l3ncCZeg6rjMYZsKfeFs9gqgCV/Y2eeopFtXCkkfYKr6q1EpxUsiVfCegsktThkrcTEX9kF3zzxd74Kcq5No6vgb1n3ZFDwcYs338SZ8CP68qvKn925pm5CeWvxJcxG+JoUxw9avZNXKIC5InilSoaS1QxXLNNuoKXzNtgdY+kVy3+gothEVu7hyq2uZdO+EG9YcaKrCzSFdmzEUbhj+HGH0aTP4FVwUZAzfMNrbx9AsFSB75Y1d2rZXNzau4AlRA+/crPTkdf/nrZJmNGI4cORvSL2XUD4XBqLhnpqq/LH01I1/9axHs1MAzbnSI965BuPkUK0yCKLYz1xicvpNG3d6Vq4lvOdvTLkTtVxoWAkEqVVE7UDPQBPDmhXqRr6qv/OdBuFjPv/pt5Xq4JYqfK1tM5PXf4AO4CHD7UxJlCxFv+mJp1n1ALnrUmZJEnlJq4SqkldGp6z/dUvq8j2Fc/F7wcZhrxAiPXFxmp7/mcLHjLpxY6ORGfbQp0ArsWJdyggmiT/Dyueck8fGpG6Y4wmblF1ir25Fu0kO2p2ptq42rrZTmdQWLt/vVwEV7gSeariKT2A59XgVl785tL3nUTKbck98kfReeajnk+16HR6Kha2fGOFLtdEXAN+oAM2iB/hsc9qVNtW5A4x3FCq9KzNlXXbsioq+DlkZDIN5MKPyQCyGxEOCvdEjBD2sQalqoRB74SfaTbjYXGdTiveXyv8mj/QzLfLGiNAaYAvXpfYijO8SlLZpSNPzS4VrdPrwzfkabMQrQP0iyobhRSdF5+GfOO95t9Q5qKtg4iYYRRdP//S0g28YQao5I59LgubVnjubUz752uO+AUKTm7t++CMSo68boQaFz4VtMVbDEdEKkLRyX2JCVMmrJ9VOGcdE96C/bCON5A4rFDRaAQyP92uOHvn04D0jat3zQ/embU5JznDuhNt7gH6qAl5i1ic9dd1/Ik4BkrIPdRRtaqZxKv0MUTyD9FfSGkistp3gKn/drjre3HlL79PWUPGNNW9t6nQmiwW+S/nLVZ/MSC5+KYxflTuDAwrLr0ks2Pcub1d3hEjS65EofI1jbeiRJOmPqk05NCC37OVrVnzdwb0m1r8Vr5L/Ad/AMSOUhJAmaPBh7wESCvcNgwE2G568TCMVChusIKewP/CZrps+eHv17NlKqPjI25j6KiPicb300JPx6qP2TmFTgP7L914rO9iLzVbwHi0Pj+QuwsT9u9P7FXtkWfKauzY1TZLFF0aQK6o6NuRDQNJnJVcmFJbPkxzy9pYifE0IMMoGEs7Wa8MCmbPPYUQwgcAe3S9D0bQIZf0PI9LQ0PUAMDwHFO6bKRHpRVjU7fSzHfmQ8E1sZzV8asnEvvut5BaBr3vQrPG6aVCSE5IeYMCS3X0SCyvWQOPeaunC14QBh9J1JJpuHZhfdotu4QQAiGVeQzGN8LHEWq4AiQUV02hr21eoT2oAdWoxRTRFV4m0FMOdFvhpzUPpJXsUgiEEu6W9ZQrQ570vorSxHuPU+/BfG3JdBlOpSCqruaUxw3ktMa/8VaxbWzDc0o5G6ot4inaWLAYNzN/fE1/AEnQxg40w2GJgGXkcH0PMbiEehrWIpjHn4UJ0gxANIBPmTwMT8kp/TKhjBfjqZoCz8IEKch4S+hbNenH36kA8cXtEGHeAta/bwsfc+63d6XGmKMGilTe0vaKDVKnNP4w0lqk9QFLR/tFYrfsYVlArI0yFBlacg6A3QSibYJzuUOpcZWdOt9t/bPqV533R779qRw/Z1TaWMyWREXoDyg7DlrIBkIJfQcCL+GBSfnlVCSFP+qIRSF6njnIqlNIvTX+4DCNoIJBQsHcSNt/+E+2Ak7Ii9EH4GDrgT5wq/6zHln9sMMtzd/XSiq7RUeq4evcq5Qi+9N1LQBF+UZIRe0l0TjCtVlicPE8Idm8wMJ5ltd3FpihAfP6+OxlhCzWjx5NI2N+xrKsZok7JtWD/qISNVvPzoyXb27tatbsTsQk/q58OeiEI2wjZdPTuzFifmza8gNYnaUfbXFldfRj1MuhPwakjTREJND0pd98EIaHbj7QvX4jjmH38hVTZ55ZM7Xkq0PqYWa7vygM32WXlKa8eT6wh1Ch1Pz4wLvHrYGnmFg9/GHEKhnoQjSaGwAOGFCAxt3wkkbT9eJHT7WNuW0UFe5FW1cwpmZp0LtjGtaK8ZhhTan8Z9trNF+OnnG7sUrzwJ8EMRVo8wPB01170tbEX49Lzv2YD6VaAxLySJEEdGzDeheV0DG8VpoS/X10jzTowMdbQUqk33Gak9c8tHytTMQfegcYTydB+z5Wkxz4TKP7C9cPux67itwMt76scbMiPdCnABaOHb4FG9/RFIGR5QnxDOJ1ROiZuVcho6iSkOchaXdX7t4g9fAquIQlGqUqcdHjJ+NjN/lAu3zo4xuaKKoehdaW/soHkU6I+F7TRNnjrVltUlMiOGOETkmOvPntNcxC+JhQtnEz74utUNlKLLtKUQNjFGzit0q8soupsj5klfI0XhbKSoHuAxNyKOWD5lxqCcD6aJQ3v6q/3jI59NZx8GKE9aPG+q5yt2TxGSYbClOvLRsVvaQpfUdH1nXgre7lxy/97CjU1ZEBQjqDEvD0TEfQQduHDfD0vUT55V2a/gu+r0/z++2pKv2/AdWZSYXmvslMx3/qqAW/FZpkpfOA6OWHEhrKAFUDzgAnVPi/cZyxo3SZVpFt2jYvb5KvBIiVvzJgxjlr5ir6I3ukrMxKHk4kctZVfv7x69erG8LGS9Lj/+OI3b+vgbsTJHjHu9/ueCjbXrIPfRgSsAJLSei7WMTp9jyL0/10YM+nNu8ddHZEbQcePH9+5Wu6AGEc+WJLoIBim16qExNrh+sNUWbs/ALu7xOkuXbpou3QbFcBfS4raqMdxwmG0v3JB5TNavzsoIAVIKiibgSnDmKAImFy4fn6PLz+ShD9u3E871MpyBkz5DEznUp2U9q9vUG0nmibserFf3BCihNTJd+asyMF29sAezfKXFIKTxQIrH2ip87XKCq2sXwXovnxvZ/i4/2Jm9xMokw3lNINPlfiUssy+Ye/209Pv6C61td8BC3SKQtgwNCAMcz+2tMBeAsqfai/VvQfho1MI/JGd9olA3z5wiIBKrpsyYtM3Wkm/CoDzzF6A8oU89v3iaqDrf7RsVP+ii9NC+f/gmTNtnY46pzBJvZ8TOgKzDwyffoT+HYOci0VR6unHli9ffkIPz3Bn3x4gqYDRU64ubCjsUwHiPysfcmFRo6F46H/x9S/ak9n3jdBTJiTt1hnto4h4SD1R+3Mq0x5Y9g1YFhiyTiJuf2bhp+9/opf37OwsmBKHbza198cxuP9t3RYbSy48PhVAsouXcINGYKregNHEX1S8jJ2pfchElAGhSpsxI8pRqf6cSPy3+No7BC72C+gxYm2wy7VTV+TkYMVO/xPT5XCC0Z3AntRVhbw1/dqC8w3pTSrAgMIDmdg8MqKhYKh/tXEfW6HvDvWCzuhJd03mVfw1nNnXS4/hheHqbyc7R//PtrffNxSzr7W3HK3GBzFR8ysifFDnlSibm+OsSQXAqZp/9IvRwgI4/uW10hDtstGqkZmV1ZHwmLegeFk6uzzcCidm5S9e+BezmgXjvznnBX7HEOPqC+OHrHezRbwqQEJ+RTrmMUPNqkjQeIQ4dPJYmz8EDacTYOTkO4cSRfoEA/xVOlFwRVGnr1q26AOd8KEAK9u9rcMrnoS8KgBl/CmM/Z5lQ/YOD8ksf7F5ZjGTOWXaFBwli4OY9J7rL1RVkCkQ/lKzeGrAg7nv1zgTyfgjiMKEa4a3u4Ywh3V/4nMrBkH4YRv7wc2Wsoy4bHeurHnLnHj3dCIkrGzqFT7Bl8/vLlqy0HThazWuOS+KYVNgImbwoepvR6Vs9hoOd4kCUKr+wiA5Q+CUid9jqdnUmY83hkZOnH473KvvIe+SNvBW3lsa1tMftrLbn5y54Tg+RkPKBb/FXO0gCG/8a2lule+8dE8bRhkOJgzTI8jmklF9G0+wsoqLkbdNuxET7PeB363+QdET9E95nyz6e1AwOgpX16mPo4dC/H/wD6dkYdWhbj6n0W4N0CnGdhuIxQRPyhwIhQjTLOimOKp35apSDiwcW1Nl/KYLsSx/yfzf+S1nQgHtPmGsw4zCUHAwUHTasIEv//nMG9bP8Hf1jZsCyILfHSgRs8th+vXf7hsXLTYbrwc+ytrYF8C11dUjPeBXKE7FueOV0wBg+TDVwFTmsHXbao/TQbjE6g0sKfpcSAJTmwSjaaOT1/1OW+5twNHUb6Oprx3cwG1RR5DQmNYUkCXpKvkzwroQQm3dkzlpxv245k13QCXcu4pQncmFyz7aah2XvjFrR79H1dVOwuh1E0r24ozGILr4KI6020FF3crM4Vs2+Mbgnts4DRRSlHarVXiED54kpixwZ83ctwuOHv6ikekt7KPn88IofK1Fxg9ZrTly5n73pyUZehqHAGx8nGAIkwFgfFklOzPj9xhA4ReUqzFPQ/gd/BZsogB43E1rjr3QRHazTb6gADjTBtuM08JVCyytLrOStha4geHwQSM0cLDi47m55l3abIQXM2HrFSCpr5oCayHaTMTB4HKqLD+Y8sGWddqk+6Fkuncsw0AtzF823/LpabD1MqN8vQIozJ5mBjI9ONC11jnOVXv1UunB5wmTlZUlUcoMObck4nzeE29Lea9XADjebghbhajYjiVfp1X0TwnHcEyHeurFDwXdnLvkw3/phY90OAStCCpREjYFwLTD0imVpEiTjQgBUUDvGYGPdFg24NM9vTH+G9xnrr+a2BllqfWPlU3MmfU/Lpl8qh868iGZHCMNDCebuCOvzCr6mtsXDpPeuvELUbY6Z76hs/h00w4RIHNRkhgiWl7JyMR1yGuGCYmitd1QUAvi8UpNYCOiUTCZS33CyqEr6phl9KkYYgQ3FlW+MQLfHGAZZ0J/F2lCDXeO66VrqTMQ0pjixAZSrqkyaJygNnE0hSeS09FGrFu4GIT7+ay1wR/UUN0E4/ZwtU2o6OIoU27t5Us+asKEtVe1US5gBBp5jCmQEcqhgtX2POheIDHKJJws1rqfGYkywiPOHLrOCHxzgMUQQFuHjVEcnUWyhRlxrxZVgfUeNeVeQ3aERYyZhhYKEN4nTv4qrGcO+Ks9VV23+SvTnPPDrgBydJsrrWpATlmVUdy4dvbnaWlpjYEzRvFFGnzYK4YAcG0a+pUVDQMj8whC964xghvLyL1sHfrcBxxvGcETJCwdnTWtP1Fk7NEgrVTiUhDytVdta9u5ev782iBx+SwuYzHIialY2KY7zMbjweFyn1zqzOSCHzF2nv4FwozxP8GtvKyw8CMolHUPlq6jK1XHYzhy+T6cun414hfrg/QYjnHQumq5klelT5rxoSTIn/M+nX/QDE6AlzZuFTYDYbA4OGfWrUUIYc5CE0LJaBv7hxCQZR9K5oSfjjijRpfg6Prn0etc7bUdGW2LefsDHBdIj5wwzZT9G+glic/jybwyYmIio9yQv94XK1SVTVtqhlB+UsVjFmkBJr5oBpuXlpXVevStd80RkryqScF7IEW5VrIsLarf3eSRF+wrFEqEVQGw6SHBqutXJeXoVvga0I+a9GDr+BkevTwlZUIbMzCOnDT9FocSs1Mw9kscOoPQiOAebBydd0tWVo/goNxL45BKYtlqnDsp72+gT4VDGeE911gqgji1WcAWY1jcobFnf0yrru3/ffNt9wx3zwn8bUzWnYkZk+7OlSldDkdcn8Ah3UtqPYHCo55wTw3uDfc8sP3BgZhfWlH5aPOxXsDIGVtiNm40fF8b5+szJt/1QdqUGQMCxZ9x692DM6fctVhVpF343k2pM2yonwZK31s5mpS7926MP/O9ZYYqTRuGSs7EdSNTqemrb5hOxQvV4qgjwT/HHYE5ap2U+/nKeV83tFv96WLH6wZRoY7Czpfb0dlZ4lpWqRRXtPjdiga6wfzS+ssMmGNbMEBWlEVoYobeK1T88ZMxeUYx9gWEJO4RBgeuYeGVuM/Hhu69CzWyCdVfxb7Lh7NqRN7H81cHWNytGIvpXL2z3hfglhz6F0Qm328VVRyx8KZVuD3xQuCIr2S90cV3D4Xw6+kLp8OTj0Df2bYhQ1xwBH0ZKIB15cSt6I0Mrd83xZur8uCHiO450FR+c093UsdZvXX4bi2Ar9GLwDw4ahOSY5Z5+L7HpJ3MLTj7w/cpLes/uUrRbchfUACFr4qEJmGczNS2qVvBS8qP+nwAl8BmK3CHE6fWsxUUvH9cLw/1CnDyRLs1MF5MXWTQxRBuHBU2+590wfoBmj17NqcueSbq6fJTtFllI3LZ0DpKvQJoR7Jh3aEwEmqOnTgz4gv2pFjBS/6Kd7/EaY6/twJ3uHByl3OeEdoXhgBgwCCZYwSRWbCwnKkk5PlJ2SWWRCoVLZ7/ImgY+mrMqqtRPDiyfsmqZR8aWkpvVIATJ8hSrD3jmtUIeCjtS9o55mr7Fi3gRtCa49OAeYcFuEOHUpCzuO/lUaMEGxXg1LR+VYTyfxpFaBY8xrY7kvIrnjEL38V4tDUCJ8MNKJzvvTi9Of2PIfuBopyF/zHKc6MCaIicTJlrFKGZ8LhJ71ncqPUbM3E24NL2/DFb7Y2IGDLUhTbgC+WvUMnzeZ8u+NAMmpd0sUl5FRsEE8lmIDcLB1fVV/ac7fcbK9YKRmXNbMd47YfaKp9Z/FqKh5I38xcv+IVZNNx6AA2pi/KXzEJuFh4mSb9KaFuRp92vZxbOBjxFOW+facdqxquEP4c04+fyNiA2+VeLa8A2mqfNFL7G4iU9gHaF6YBhFdsZI4NMroNxdLgwknP+vHTW+YYVF0mMvm1GGu4jnQdfQaxxZk3F8K2LkWmff7ygwFSsQHapAiAxobB8HIyM+mvFzCZoCj5BTmMF7B3hpItLx/bZbOb+wvHjZ8Y4perfYDL6BFonxhR+DSAxeumUP9JeFUADGlBQ8QXOj0nzhyDs+UIcR0zRdkolGHPiFAykSiqUGlW2H5W5Wrors6+uiKf6wyXaOP4XDTQdf/rPFdbZQFzQf0mEP5W3ZOFGnSgCAmtSARJWlA6kdvt2dBJh3zsQUE2aLEQrsDT7j9qzZ98on3xt0D5zLebO6Yx5CMfYP4CexuKNtFBfKpaqnL6GOwjWNVklEzOaVACNRlLe/hdx8PCvTaQXNlSwoaoQPvX43jGxulyn2h3AakznDMTrT4XlPIEgRNukynA4vDYJTnNqJPWjtZ8s+q9JeANC41MB+rz3RVRM917bMRYGHPcWENVwFhLkhdLMuKeNsKApgyuqy1DEU6Yi3OtGxAgOhBe1Z4CRvd9iBW834+JL3Dy6roqKVZuXLDxphB8jsD4VQEM8IH//YIQOYxyiIR8HjVTMF6wixB24htZUr6emFKR1xzheJ7oqNns77OZqZWMywsLUKoXRKupyVjKHWpGfk3PKF2+hzvOrABpDiblljxBJej3UzFlI79jJo63iQnUxlYX1MIz6EkeQN4ylY/rPwUBliuvRG/4wpHVt36X6jjDQjTiSASmAxnXt4UP34mICS6ckoWwdKvGxoaQXqbQCVoCD94yoPVmlToTFqjv+LJIaAZsiEyOJn3DxErACaAwendrvW/mcGAFX6dfhYtgsuogHsPZ8IrMYtRhPUAqg8fLVlH7fSAofCZ95ucW8WYwe5yJcfvTdm7drbL9y+bzmJhbN+SjVfZfljy0sehtB6wns58+mYNdNkV4c4YTDySFrw0k/UmjrVgCtAjsmXVcZ0/n0WKLSNyKlQgHzUS0+DrhsCy4YkCMokPonFu6bisMe3kFkjSmHJwRCU28ZXKm6Yk963Hi98C0JzlAPcHFDlKb3y8axowOxmvXFxemR979QaF3dU5HHV3g4Mq0HaGQfkUtJReX3ccFewJbsTo3pEfKPqqrP7B3TXwv/uvygBcxXgO+atfvyvZ3bO7QNmfSBCFpIyilNj73dzAii5q5Fpg0Bng1xZHz8idKMfr9kikiAz2A+powuzzKhfMdRUYtoZe20y8J3b3XLegB3MoQMzN/fU+H8ESrJ94R2aBDnCKezSkfHveXJ0+V3C4eAJhsX19T2j6e3MkanISgiA1+kJYcvIqhVRXzVIpnwZ/TGBTZZhxaUEbIewFubxRaWt0PY7UjtlDBE+aYhsqaft3LBpdEKTtSP6lyutw+MS/w6ONgfXumwKoBncydlH+qodlCG4pClBKw6xmPFLg5xCN3A5BXYK6gduFTPLxajcNsMOY0yp7F97CAMmV2I99tVW+dce2DigGa738+zPULx/v+VFImg/o6qJQAAAABJRU5ErkJggg==";

    NSData* imageData = [[NSData alloc] initWithBase64EncodedString:base64ImageString
                                                            options:NSDataBase64DecodingIgnoreUnknownCharacters];
    return [[NSImage alloc] initWithData:imageData];
}

@end
