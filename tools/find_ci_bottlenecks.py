#!/usr/bin/env python

import fileinput
import heapq

# This takes a line that looks like:
# [10:01:03]   A build step ran!
#
# And yields a tuple where the time is replaced by the number of seconds past
# midnight:
# (36063, "A build step ran!")
def extract_seconds(line):
    assert line[0] == '['
    assert line[3] == ':'
    assert line[6] == ':'
    assert line[9] == ']'
    time = int(line[1:3])*60*60 + int(line[4:6])*60 + int(line[7:9])
    message = line[10:].strip()
    return time, message


# This takes lines that look like:
# [10:01:03]   A build step ran!
# [10:01:05]   Two seconds later, another step ran!
# [10:01:45]   Wow, that last step took a whole 40 seconds!
#
# And yields tuples that show the time between a step and the next one
# (2, "A build step ran!")
# (40, "Two seconds later, another step ran!")
# etc.
def elapsed_times(lines):
    lastTime, lastMsg = None, None
    for line in lines:
        if line[0] != '[':
            continue
        thisTime, thisMsg = extract_seconds(line)
        if lastTime is not None:
            elapsedTime = (thisTime - lastTime) % (24*60*60)
            yield elapsedTime, lastMsg
        lastTime, lastMsg = thisTime, thisMsg


if __name__ == "__main__":
    total_time = 0
    print 'The 20 slowest lines were:\n'
    for time, msg in heapq.nlargest(20, elapsed_times(fileinput.input())):
        print time, msg
        total_time += time
    print '\nThese 20 lines took a total of {} seconds.'.format(total_time)
