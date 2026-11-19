#include "ab_hooks.h"
#include <sys/socket.h>
#include <netinet/in.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>
#include <unistd.h>
#include "iperf_config.h"
#include "iperf.h"
#include "iperf_api.h"
#ifdef __ANDROID__
#include <android/multinetwork.h>
#endif
static atomic_int cancelled, ready;
static atomic_flag busy = ATOMIC_FLAG_INIT;
static uint64_t network_handle;
static char temp_template[4096];
void ab_set_temp_dir(const char *directory) { snprintf(temp_template,sizeof(temp_template),"%s/iperf.XXXXXX",directory); }
int ab_socket(int domain, int type, int protocol) {
    int fd=socket(domain,type,protocol);
#ifdef __APPLE__
    if (fd >= 0) { int enabled=1; setsockopt(fd,SOL_SOCKET,SO_NOSIGPIPE,&enabled,sizeof(enabled)); }
#endif
#ifdef __ANDROID__
    if (fd>=0 && network_handle && android_setsocknetwork(network_handle,fd)!=0) {
        int saved=errno; close(fd); errno=saved; return -1;
    }
#endif
    return fd;
}
int ab_listen(int fd,int backlog) { int rc=listen(fd,backlog); if(rc==0) atomic_store(&ready,1); return rc; }
int ab_ready(void) { return atomic_load(&ready); }
int ab_cancelled(void) { return atomic_load(&cancelled); }
void ab_cancel(void) { atomic_store(&cancelled,1); atomic_store(&ready,0); }
void ab_prepare(uint64_t network) { network_handle=network; atomic_store(&cancelled,0); }
void ab_free(char *value) { free(value); }
char *ab_iperf_run(int server,const char *address,const char *device,int port,int udp,int reverse,int streams,int duration,int rate) {
    if (atomic_flag_test_and_set(&busy)) return strdup("{\"error\":\"iperf already running\"}");
    atomic_store(&ready,0);
    struct iperf_test *test=iperf_new_test();
    if (!test) { atomic_flag_clear(&busy); return strdup("{\"error\":\"allocation failed\"}"); }
    iperf_defaults(test);
    if (*temp_template) iperf_set_test_template(test,temp_template);
    test->outfile=fopen("/dev/null","w");
    FILE *output=test->outfile;
    test->settings->domain=AF_INET6;
    iperf_set_test_role(test,server?'s':'c');
    iperf_set_test_server_port(test,port);
    iperf_set_test_json_output(test,1);
    iperf_set_test_one_off(test,1);
    test->settings->idle_timeout=0;
    test->settings->rcv_timeout.secs=5;
    test->settings->snd_timeout=5000000;
    if (server) iperf_set_test_bind_address(test,address);
    else {
        if (streams<1 || streams>16 || duration<1 || duration>3600) {
            iperf_free_test(test); if(output) fclose(output); atomic_flag_clear(&busy);
            return strdup("{\"error\":\"invalid test parameters\"}");
        }
        iperf_set_test_server_hostname(test,address);
        if (device && *device) iperf_set_test_bind_dev(test,device);
        iperf_set_test_connect_timeout(test,3000);
        iperf_set_test_duration(test,duration);
        iperf_set_test_num_streams(test,streams);
        iperf_set_test_reverse(test,reverse);
        set_protocol(test,udp?Pudp:Ptcp);
        if (udp) { iperf_set_test_rate(test,(uint64_t)rate*1000000/streams); iperf_set_test_blksize(test,1200); }
    }
    int rc=server?iperf_run_server(test):iperf_run_client(test);
    char *json=iperf_get_test_json_output_string(test);
    char *result=json?strdup(json):NULL;
    if (!result) {
        cJSON *error = cJSON_CreateObject();
        cJSON_AddStringToObject(error, "error", ab_cancelled() ? "cancelled" : iperf_strerror(i_errno));
        cJSON_AddNumberToObject(error, "code", rc);
        result = cJSON_PrintUnformatted(error);
        cJSON_Delete(error);
    }
    iperf_free_test(test);
    if (output) fclose(output);
    atomic_store(&ready,0);
    atomic_flag_clear(&busy);
    return result;
}
