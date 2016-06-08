#include <errno.h>
#include <netinet/in.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>

#include "../logger.h"
#include "notif.h"

extern Logger l;

int init_socket(const char path[]) {
    struct sockaddr_un remote;

    int sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (sock == -1) {
        l.error() << "Could not create notif socket" << l;
        return -1;
    }

    remote.sun_family = AF_UNIX;
    strcpy(remote.sun_path, path);
    if (connect(sock, (struct sockaddr *)&remote, sizeof(remote)) == -1) {
        l.error() << "Could not connect to notif socket" << l;
        return -1;
    }

    l.debug() << "Connected to notif socket" << l;
    return sock;
}

int send_message(int sock, const char str[]) {
    size_t datalen = strlen(str);
    uint32_t datalen_enc = htonl(datalen);
    size_t msg_len = sizeof(datalen_enc) + datalen;

    void *msg = malloc(msg_len);
    *((uint32_t *)msg) = datalen_enc;
    memcpy(((uint32_t *)msg) + 1, str, datalen);

    ssize_t sent = send(sock, msg, msg_len, 0);

    if (sent != msg_len) {
        l.error() << "Could not send message to notif socket" << l;
        return -1;
    }

    l.debug() << "Sent message '" << str << "' to notif socket" << l;
    return 0;
}

void close_socket(int sock) {
    if (close(sock) != 0) {
        l.error() << "Failed to close notif socket" << l;
    }
}
