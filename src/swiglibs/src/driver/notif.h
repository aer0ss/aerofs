#ifndef __NOTIF_H__
#define __NOTIF_H__

void init_socket(const char path[]);
void send_message(const char str[]);
void close_socket();

#endif
