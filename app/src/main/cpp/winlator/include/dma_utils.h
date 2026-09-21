#ifndef WINLATOR_DMA_UTILS_H
#define WINLATOR_DMA_UTILS_H

#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <linux/dma-heap.h>
#include <errno.h>

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>

#include "ion/ion.h"
#include "ion/ion_4.19.h"

#define ION_SYSTEM_HEAP_ID_MASK (1u << 25)

/*
 * ============================================================
 * WINLATOR MEMORY CONFIGURATION
 * ============================================================
 *
 * WINLATOR_RAM_MB:
 *
 * 0 / não definido = memória real do Android/Linux
 * 2048             = 2 GB
 * 3072             = 3 GB
 * 4096             = 4 GB
 * 5120             = 5 GB
 * 6144             = 6 GB
 * 7168             = 7 GB
 * 8192             = 8 GB
 *
 * IMPORTANTE:
 * Isto altera o valor retornado por estas funções.
 * Não cria RAM física adicional.
 */

#define WINLATOR_MIN_RAM_MB 2048ULL
#define WINLATOR_MAX_RAM_MB 8192ULL

static uint64_t winlator_parse_ram_mb(void) {
    const char *env = getenv("WINLATOR_RAM_MB");

    if (env == NULL || env[0] == '\0')
        return 0;

    char *end = NULL;

    unsigned long long value =
        strtoull(env, &end, 10);

    if (end == env)
        return 0;

    if (value == 0)
        return 0;

    /*
     * Proteção contra valores absurdos.
     */
    if (value < WINLATOR_MIN_RAM_MB)
        value = WINLATOR_MIN_RAM_MB;

    if (value > WINLATOR_MAX_RAM_MB)
        value = WINLATOR_MAX_RAM_MB;

    return (uint64_t)value;
}

/*
 * Retorna a RAM configurada em bytes.
 *
 * 0 = usar memória real.
 */
static inline uint64_t getConfiguredSystemMemory(void) {
    uint64_t ramMB = winlator_parse_ram_mb();

    if (ramMB == 0)
        return 0;

    return ramMB * 1024ULL * 1024ULL;
}

/*
 * ============================================================
 * SAFE IOCTL
 * ============================================================
 */

static int safe_ioctl(int fd, uint64_t request, void *arg) {
    int res;

    do {
        res = ioctl(fd, request, arg);
    }
    while (res == -1 &&
           (errno == EINTR || errno == EAGAIN));

    return res;
}

/*
 * ============================================================
 * DMA-BUF ALLOCATION
 * ============================================================
 */

static inline int dmabuf_alloc(uint64_t size) {
    int res;

    /*
     * Primeiro tenta DMA-BUF system-uncached.
     */
    int dma_fd = open(
        "/dev/dma_heap/system-uncached",
        O_RDONLY
    );

    /*
     * Fallback para system.
     */
    if (dma_fd < 0) {
        dma_fd = open(
            "/dev/dma_heap/system",
            O_RDONLY
        );
    }

    if (dma_fd >= 0) {

        struct dma_heap_allocation_data alloc_data = {
            .len = size,
            .fd_flags = O_RDWR | O_CLOEXEC,
            .heap_flags = 0
        };

        res = safe_ioctl(
            dma_fd,
            DMA_HEAP_IOCTL_ALLOC,
            &alloc_data
        );

        close(dma_fd);

        if (res)
            return -1;

        return alloc_data.fd;
    }

    /*
     * ========================================================
     * ION FALLBACK
     * ========================================================
     */

    int ion_fd = open("/dev/ion", O_RDONLY);

    if (ion_fd < 0)
        return -1;

    /*
     * Testa a API ION antiga.
     */
    struct ion_handle_data free_data = {0};

    free_data.handle = 0;

    int free_result = safe_ioctl(
        ion_fd,
        ION_IOC_FREE,
        &free_data
    );

    /*
     * API ION antiga disponível.
     */
    if (free_result >= 0 || errno != ENOTTY) {

        struct ion_allocation_data alloc_data = {
            .len = size,
            .align = 4096,
            .heap_id_mask = ION_SYSTEM_HEAP_ID_MASK,
            .flags = 0,
            .handle = -1
        };

        res = safe_ioctl(
            ion_fd,
            ION_IOC_ALLOC,
            &alloc_data
        );

        if (res) {
            close(ion_fd);
            return -1;
        }

        struct ion_fd_data share = {
            .handle = alloc_data.handle,
            .fd = -1
        };

        res = safe_ioctl(
            ion_fd,
            ION_IOC_SHARE,
            &share
        );

        if (res) {
            close(ion_fd);
            return -1;
        }

        free_data.handle = alloc_data.handle;

        res = safe_ioctl(
            ion_fd,
            ION_IOC_FREE,
            &free_data
        );

        close(ion_fd);

        if (res)
            return -1;

        return share.fd;
    }

    /*
     * ========================================================
     * ION NEW API
     * ========================================================
     */

    struct ion_new_allocation_data alloc_data = {
        .len = size,
        .heap_id_mask = ION_SYSTEM_HEAP_ID_MASK,
        .flags = 0,
        .fd = -1
    };

    res = safe_ioctl(
        ion_fd,
        ION_IOC_NEW_ALLOC,
        &alloc_data
    );

    close(ion_fd);

    if (res)
        return -1;

    return alloc_data.fd;
}

/*
 * ============================================================
 * REAL SYSTEM MEMORY
 * ============================================================
 */

static inline uint64_t getRealTotalSystemMemory(void) {

    FILE *file = fopen(
        "/proc/meminfo",
        "r"
    );

    if (!file)
        return 0;

    uint64_t memTotalKB = 0;

    char line[256];

    while (fgets(
        line,
        sizeof(line),
        file
    ) != NULL) {

        if (strncmp(
            line,
            "MemTotal:",
            9
        ) == 0) {

            unsigned long long value = 0;

            if (sscanf(
                line,
                "MemTotal: %llu kB",
                &value
            ) == 1) {

                memTotalKB = (uint64_t)value;
            }

            break;
        }
    }

    fclose(file);

    return memTotalKB * 1024ULL;
}

/*
 * ============================================================
 * REAL AVAILABLE MEMORY
 * ============================================================
 */

static inline uint64_t getRealAvailableSystemMemory(void) {

    FILE *file = fopen(
        "/proc/meminfo",
        "r"
    );

    if (!file)
        return 0;

    uint64_t availMemKB = 0;

    char line[256];

    while (fgets(
        line,
        sizeof(line),
        file
    ) != NULL) {

        if (strncmp(
            line,
            "MemAvailable:",
            13
        ) == 0) {

            unsigned long long value = 0;

            if (sscanf(
                line,
                "MemAvailable: %llu kB",
                &value
            ) == 1) {

                availMemKB = (uint64_t)value;
            }

            break;
        }

        /*
         * Fallback para kernels que não possuem
         * MemAvailable.
         */
        else if (
            strncmp(
                line,
                "MemFree:",
                8
            ) == 0 &&
            availMemKB == 0
        ) {

            unsigned long long value = 0;

            if (sscanf(
                line,
                "MemFree: %llu kB",
                &value
            ) == 1) {

                availMemKB = (uint64_t)value;
            }
        }
    }

    fclose(file);

    return availMemKB * 1024ULL;
}

/*
 * ============================================================
 * CONFIGURED TOTAL MEMORY
 * ============================================================
 *
 * Esta é a função principal modificada.
 *
 * Se WINLATOR_RAM_MB estiver configurado:
 *
 *     WINLATOR_RAM_MB=8192
 *
 * retorna:
 *
 *     8 GB
 *
 * Caso contrário, retorna a RAM real.
 */

static inline uint64_t getTotalSystemMemory(void) {

    uint64_t configuredMemory =
        getConfiguredSystemMemory();

    if (configuredMemory > 0)
        return configuredMemory;

    return getRealTotalSystemMemory();
}

/*
 * ============================================================
 * CONFIGURED AVAILABLE MEMORY
 * ============================================================
 *
 * Quando uma RAM artificial é configurada, não podemos
 * simplesmente retornar a memória real do Android, pois isso
 * produziria algo incoerente:
 *
 * Total = 8 GB
 * Available = 1 GB
 *
 * Por isso calculamos uma estimativa proporcional baseada
 * na memória real disponível.
 *
 * IMPORTANTE:
 * isto é apenas um valor REPORTADO.
 */

static inline uint64_t getAvailableSystemMemory(void) {

    uint64_t configuredMemory =
        getConfiguredSystemMemory();

    /*
     * Auto:
     * usa o valor real.
     */
    if (configuredMemory == 0)
        return getRealAvailableSystemMemory();

    uint64_t realTotal =
        getRealTotalSystemMemory();

    uint64_t realAvailable =
        getRealAvailableSystemMemory();

    if (realTotal == 0)
        return configuredMemory;

    /*
     * Mantém a proporção de memória disponível.
     */
    uint64_t available =
        (realAvailable * configuredMemory) /
        realTotal;

    /*
     * Nunca ultrapassa o total configurado.
     */
    if (available > configuredMemory)
        available = configuredMemory;

    return available;
}

/*
 * ============================================================
 * MEMORY STATUS
 * ============================================================
 */

static inline uint64_t getSystemMemoryUsed(void) {

    uint64_t total =
        getTotalSystemMemory();

    uint64_t available =
        getAvailableSystemMemory();

    if (available >= total)
        return 0;

    return total - available;
}

/*
 * Retorna a quantidade configurada em MB.
 *
 * 0 = Auto.
 */
static inline uint64_t getConfiguredSystemMemoryMB(void) {

    uint64_t memory =
        getConfiguredSystemMemory();

    if (memory == 0)
        return 0;

    return memory /
           (1024ULL * 1024ULL);
}

#endif
