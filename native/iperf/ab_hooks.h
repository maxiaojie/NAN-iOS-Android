#ifndef AB_HOOKS_H
#define AB_HOOKS_H
#include <stdint.h>
int ab_socket(int domain, int type, int protocol);
int ab_listen(int fd, int backlog);
int ab_ready(void);
int ab_cancelled(void);
void ab_cancel(void);
void ab_set_temp_dir(const char *directory);
void ab_prepare(uint64_t network);
char *ab_iperf_run(int server, const char *address, const char *device, int port, int udp, int reverse, int streams, int duration, int rate);
void ab_free(char *value);
#endif
