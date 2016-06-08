#ifndef __NOTIF_H__
#define __NOTIF_H__

int init_socket(const char path[]);
int send_message(int sock, const char str[]);
void close_socket(int sock);

#endif
