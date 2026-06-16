cmake_minimum_required(VERSION 3.10.2)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp)

add_library(
        tun2http
        SHARED
        src/main/cpp/dhcp.c
        src/main/cpp/dns.c
        src/main/cpp/icmp.c
        src/main/cpp/ip.c
        src/main/cpp/http.c
        src/main/cpp/tun2http.c
        src/main/cpp/session.c
        src/main/cpp/tcp.c
        src/main/cpp/tls.c
        src/main/cpp/udp.c
        src/main/cpp/util.c
)

find_library(log-lib log)

target_link_libraries(tun2http ${log-lib})
