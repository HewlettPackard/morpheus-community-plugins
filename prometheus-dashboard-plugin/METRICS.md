# Metrics Reference — Prometheus Dashboard Plugin

This document lists all Prometheus metrics collected and used by the plugin, grouped by source and category.

- **Section 1** — Hypervisor & Guest metrics (via `libvirt_exporter`)
- **Section 2** — HVM OS metrics (via `node_exporter`)

---

## 1. Hypervisor & Guest Metrics

Collected by the **Prometheus Libvirt Exporter** running on each HVM host.

### 1.1 Domain / VM Information

| Metric                                       | Description                       |
| -------------------------------------------- | --------------------------------- |
| `libvirt_domain_info`                        | Metadata labels for the domain/VM |
| `libvirt_domain_info_cpu_time_seconds_total` | Total CPU time used by the VM     |
| `libvirt_domain_info_maximum_memory_bytes`   | Maximum configured memory         |
| `libvirt_domain_info_memory_usage_bytes`     | Current memory usage              |
| `libvirt_domain_info_state`                  | VM state code                     |
| `libvirt_domain_info_virtual_cpus`           | Number of configured vCPUs        |
| `libvirt_domain_openstack_info`              | OpenStack metadata labels         |
| `libvirt_domains`                            | Total number of domains           |
| `libvirt_domain_timed_out`                   | Whether metric scraping timed out |
| `libvirt_up`                                 | Whether libvirt scrape succeeded  |

### 1.2 vCPU Metrics

| Metric                                    | Description                |
| ----------------------------------------- | -------------------------- |
| `libvirt_domain_vcpu_current`             | Current online vCPUs       |
| `libvirt_domain_vcpu_maximum`             | Maximum online vCPUs       |
| `libvirt_domain_vcpu_state`               | Current vCPU state         |
| `libvirt_domain_vcpu_time_seconds_total`  | CPU execution time         |
| `libvirt_domain_vcpu_wait_seconds_total`  | Time waiting for scheduler |
| `libvirt_domain_vcpu_delay_seconds_total` | Scheduler queue delay time |

### 1.3 Memory Metrics

| Metric                                                      | Description                     |
| ----------------------------------------------------------- | ------------------------------- |
| `libvirt_domain_memory_stats_available_bytes`               | Memory available to guest       |
| `libvirt_domain_memory_stats_current_balloon_bytes`         | Current balloon memory          |
| `libvirt_domain_memory_stats_disk_caches_bytes`             | Guest disk cache memory         |
| `libvirt_domain_memory_stats_hugetlb_pgalloc_total`         | Successful hugepage allocations |
| `libvirt_domain_memory_stats_hugetlb_pgfail_total`          | Failed hugepage allocations     |
| `libvirt_domain_memory_stats_last_update_timestamp_seconds` | Last memory stat update         |
| `libvirt_domain_memory_stats_major_fault_total`             | Major page faults               |
| `libvirt_domain_memory_stats_minor_fault_total`             | Minor page faults               |
| `libvirt_domain_memory_stats_maximum_bytes`                 | Maximum memory usable           |
| `libvirt_domain_memory_stats_rss_bytes`                     | Resident memory usage           |
| `libvirt_domain_memory_stats_swap_in_bytes`                 | Swap-in bytes                   |
| `libvirt_domain_memory_stats_swap_out_bytes`                | Swap-out bytes                  |
| `libvirt_domain_memory_stats_unused_bytes`                  | Unused guest memory             |
| `libvirt_domain_memory_stats_usable_bytes`                  | Usable guest memory             |
| `libvirt_domain_memory_stats_used_percent`                  | Memory usage percentage         |

### 1.4 Disk / Block Device Metrics

| Metric                                                | Description               |
| ----------------------------------------------------- | ------------------------- |
| `libvirt_domain_block_stats_info`                     | Block device metadata     |
| `libvirt_domain_block_stats_capacity_bytes`           | Logical disk size         |
| `libvirt_domain_block_stats_read_bytes_total`         | Total bytes read          |
| `libvirt_domain_block_stats_write_bytes_total`        | Total bytes written       |
| `libvirt_domain_block_stats_read_requests_total`      | Read requests             |
| `libvirt_domain_block_stats_write_requests_total`     | Write requests            |
| `libvirt_domain_block_stats_read_time_seconds_total`  | Time spent reading        |
| `libvirt_domain_block_stats_write_time_seconds_total` | Time spent writing        |
| `libvirt_domain_block_stats_flush_requests_total`     | Cache flush requests      |
| `libvirt_domain_block_stats_flush_time_seconds_total` | Time spent flushing cache |

### 1.5 Network Metrics

| Metric                                                  | Description                |
| ------------------------------------------------------- | -------------------------- |
| `libvirt_domain_interface_stats_info`                   | Network interface metadata |
| `libvirt_domain_interface_stats_receive_bytes_total`    | RX bytes                   |
| `libvirt_domain_interface_stats_transmit_bytes_total`   | TX bytes                   |
| `libvirt_domain_interface_stats_receive_packets_total`  | RX packets                 |
| `libvirt_domain_interface_stats_transmit_packets_total` | TX packets                 |
| `libvirt_domain_interface_stats_receive_errors_total`   | RX errors                  |
| `libvirt_domain_interface_stats_transmit_errors_total`  | TX errors                  |
| `libvirt_domain_interface_stats_receive_drops_total`    | RX dropped packets         |
| `libvirt_domain_interface_stats_transmit_drops_total`   | TX dropped packets         |

### 1.6 Domain Job Metrics

| Metric                                           | Description         |
| ------------------------------------------------ | ------------------- |
| `libvirt_domain_job_info_type`                   | Current job type    |
| `libvirt_domain_job_info_time_elapsed_seconds`   | Elapsed job time    |
| `libvirt_domain_job_info_time_remaining_seconds` | Remaining job time  |
| `libvirt_domain_job_info_data_processed_bytes`   | Processed data      |
| `libvirt_domain_job_info_data_remaining_bytes`   | Remaining data      |
| `libvirt_domain_job_info_data_total_bytes`       | Total data size     |
| `libvirt_domain_job_info_memory_processed_bytes` | Processed memory    |
| `libvirt_domain_job_info_memory_remaining_bytes` | Remaining memory    |
| `libvirt_domain_job_info_memory_total_bytes`     | Total memory        |
| `libvirt_domain_job_info_file_processed_bytes`   | Processed file data |
| `libvirt_domain_job_info_file_remaining_bytes`   | Remaining file data |
| `libvirt_domain_job_info_file_total_bytes`       | Total file data     |

### 1.7 Storage Pool Metrics

| Metric                                  | Description            |
| --------------------------------------- | ---------------------- |
| `libvirt_storage_pool_allocation_bytes` | Allocated storage      |
| `libvirt_storage_pool_available_bytes`  | Free storage           |
| `libvirt_storage_pool_capacity_bytes`   | Total storage capacity |
| `libvirt_storage_pool_state`            | Storage pool state     |
| `libvirt_storage_pool_timed_out`        | Pool scrape timeout    |

---

## 2. HVM OS Metrics

Collected by **Prometheus Node Exporter** running on each HVM host.

### 2.1 CPU Metrics

| Metric                                 | Description                      |
| -------------------------------------- | -------------------------------- |
| `node_cpu_seconds_total`               | CPU time spent in each mode      |
| `node_cpu_guest_seconds_total`         | CPU time spent running guest VMs |
| `node_cpu_frequency_hertz`             | Current CPU frequency            |
| `node_cpu_frequency_max_hertz`         | Maximum CPU frequency            |
| `node_cpu_frequency_min_hertz`         | Minimum CPU frequency            |
| `node_cpu_scaling_frequency_hertz`     | Current scaled frequency         |
| `node_cpu_scaling_frequency_max_hertz` | Max scaled frequency             |
| `node_cpu_scaling_frequency_min_hertz` | Min scaled frequency             |
| `node_cpu_scaling_governor`            | Current CPU governor             |
| `node_schedstat_running_seconds_total` | CPU running time                 |
| `node_schedstat_waiting_seconds_total` | CPU wait time                    |
| `node_schedstat_timeslices_total`      | Executed timeslices              |
| `node_load1`                           | 1-minute load average            |
| `node_load5`                           | 5-minute load average            |
| `node_load15`                          | 15-minute load average           |
| `node_context_switches_total`          | Total context switches           |

### 2.2 Memory Metrics

| Metric                           | Description                |
| -------------------------------- | -------------------------- |
| `node_memory_MemTotal_bytes`     | Total RAM                  |
| `node_memory_MemAvailable_bytes` | Available RAM              |
| `node_memory_MemFree_bytes`      | Free RAM                   |
| `node_memory_Cached_bytes`       | Cached memory              |
| `node_memory_Buffers_bytes`      | Buffered memory            |
| `node_memory_SwapTotal_bytes`    | Total swap                 |
| `node_memory_SwapFree_bytes`     | Free swap                  |
| `node_memory_Slab_bytes`         | Kernel slab memory         |
| `node_memory_PageTables_bytes`   | Memory used by page tables |
| `node_memory_KernelStack_bytes`  | Kernel stack memory        |
| `node_memory_AnonPages_bytes`    | Anonymous pages            |
| `node_memory_Mapped_bytes`       | Memory mapped files        |
| `node_memory_Dirty_bytes`        | Dirty pages                |
| `node_memory_Writeback_bytes`    | Pages being written back   |
| `node_memory_HugePages_Total`    | Total hugepages            |
| `node_memory_HugePages_Free`     | Free hugepages             |
| `node_memory_Hugepagesize_bytes` | Hugepage size              |

### 2.3 Disk / Filesystem Metrics

| Metric                                        | Description                 |
| --------------------------------------------- | --------------------------- |
| `node_disk_read_bytes_total`                  | Total disk read bytes       |
| `node_disk_written_bytes_total`               | Total disk written bytes    |
| `node_disk_reads_completed_total`             | Completed disk reads        |
| `node_disk_writes_completed_total`            | Completed disk writes       |
| `node_disk_read_time_seconds_total`           | Disk read time              |
| `node_disk_write_time_seconds_total`          | Disk write time             |
| `node_disk_io_now`                            | Current I/O operations      |
| `node_disk_io_time_seconds_total`             | Total I/O time              |
| `node_disk_io_time_weighted_seconds_total`    | Weighted I/O time           |
| `node_disk_flush_requests_total`              | Flush requests              |
| `node_disk_flush_requests_time_seconds_total` | Flush request time          |
| `node_filesystem_size_bytes`                  | Filesystem size             |
| `node_filesystem_free_bytes`                  | Free filesystem space       |
| `node_filesystem_avail_bytes`                 | Available filesystem space  |
| `node_filesystem_files`                       | Total inodes                |
| `node_filesystem_files_free`                  | Free inodes                 |
| `node_filesystem_readonly`                    | Read-only filesystem status |

### 2.4 Network Metrics

| Metric                                | Description                  |
| ------------------------------------- | ---------------------------- |
| `node_network_receive_bytes_total`    | RX bytes                     |
| `node_network_transmit_bytes_total`   | TX bytes                     |
| `node_network_receive_packets_total`  | RX packets                   |
| `node_network_transmit_packets_total` | TX packets                   |
| `node_network_receive_drop_total`     | RX dropped packets           |
| `node_network_transmit_drop_total`    | TX dropped packets           |
| `node_network_receive_errs_total`     | RX errors                    |
| `node_network_transmit_errs_total`    | TX errors                    |
| `node_network_speed_bytes`            | Interface speed              |
| `node_network_mtu_bytes`              | MTU size                     |
| `node_network_up`                     | Interface operational status |
| `node_network_carrier`                | Carrier state                |

### 2.5 TCP / UDP / Socket Metrics

| Metric                            | Description              |
| --------------------------------- | ------------------------ |
| `node_sockstat_TCP_inuse`         | TCP sockets in use       |
| `node_sockstat_TCP_tw`            | TCP TIME_WAIT sockets    |
| `node_sockstat_TCP_alloc`         | Allocated TCP sockets    |
| `node_sockstat_UDP_inuse`         | UDP sockets in use       |
| `node_sockstat_sockets_used`      | IPv4 sockets in use      |
| `node_nf_conntrack_entries`       | Active conntrack entries |
| `node_nf_conntrack_entries_limit` | Conntrack table limit    |
| `node_udp_queues`                 | UDP queue memory         |

### 2.6 System Metrics

| Metric                        | Description               |
| ----------------------------- | ------------------------- |
| `node_boot_time_seconds`      | System boot time          |
| `node_time_seconds`           | Current system time       |
| `node_procs_running`          | Running processes         |
| `node_procs_blocked`          | Blocked processes         |
| `node_forks_total`            | Total process forks       |
| `node_intr_total`             | Total interrupts          |
| `node_entropy_available_bits` | Available entropy         |
| `node_entropy_pool_size_bits` | Entropy pool size         |
| `node_selinux_enabled`        | SELinux enabled status    |
| `node_uname_info`             | Kernel/system information |
| `node_os_info`                | Operating system metadata |
| `node_os_version`             | OS version                |
| `node_arp_entries`            | ARP table entries         |

### 2.7 Pressure Stall Information (PSI)

| Metric                                       | Description            |
| -------------------------------------------- | ---------------------- |
| `node_pressure_cpu_waiting_seconds_total`    | CPU pressure wait time |
| `node_pressure_io_waiting_seconds_total`     | IO pressure wait time  |
| `node_pressure_io_stalled_seconds_total`     | IO stalled time        |
| `node_pressure_memory_waiting_seconds_total` | Memory wait time       |
| `node_pressure_memory_stalled_seconds_total` | Memory stalled time    |

### 2.8 Hardware / Sensors / Power Metrics

| Metric                           | Description                    |
| -------------------------------- | ------------------------------ |
| `node_hwmon_temp_celsius`        | Hardware temperature           |
| `node_hwmon_power_average_watt`  | Average power usage            |
| `node_rapl_core_joules_total`    | CPU core energy consumption    |
| `node_rapl_package_joules_total` | CPU package energy consumption |
| `node_cooling_device_cur_state`  | Current cooling device state   |
| `node_cooling_device_max_state`  | Maximum cooling state          |
| `node_dmi_info`                  | BIOS/system hardware metadata  |

### 2.9 VM / Kernel Metrics

| Metric                   | Description            |
| ------------------------ | ---------------------- |
| `node_vmstat_pgfault`    | Total page faults      |
| `node_vmstat_pgmajfault` | Major page faults      |
| `node_vmstat_pgpgin`     | Pages paged in         |
| `node_vmstat_pgpgout`    | Pages paged out        |
| `node_vmstat_pswpin`     | Swap pages read        |
| `node_vmstat_pswpout`    | Swap pages written     |
| `node_vmstat_oom_kill`   | OOM killer invocations |

### 2.10 Time Synchronization Metrics

| Metric                                  | Description                  |
| --------------------------------------- | ---------------------------- |
| `node_timex_offset_seconds`             | Clock offset                 |
| `node_timex_sync_status`                | Clock synchronization status |
| `node_timex_frequency_adjustment_ratio` | Clock frequency adjustment   |
| `node_timex_estimated_error_seconds`    | Estimated clock error        |
| `node_timex_maxerror_seconds`           | Maximum clock error          |
| `node_timex_status`                     | Kernel time status bits      |

### 2.11 Exporter Health Metrics

| Metric                                   | Description                     |
| ---------------------------------------- | ------------------------------- |
| `node_scrape_collector_duration_seconds` | Collector scrape duration       |
| `node_scrape_collector_success`          | Collector scrape success        |
| `node_exporter_build_info`               | Node exporter build metadata    |
| `node_textfile_scrape_error`             | Textfile collector scrape error |

### 2.12 Process Metrics

| Metric                                 | Description                |
| -------------------------------------- | -------------------------- |
| `process_cpu_seconds_total`            | Exporter process CPU usage |
| `process_open_fds`                     | Open file descriptors      |
| `process_max_fds`                      | Maximum file descriptors   |
| `process_resident_memory_bytes`        | Resident memory usage      |
| `process_virtual_memory_bytes`         | Virtual memory usage       |
| `process_virtual_memory_max_bytes`     | Maximum virtual memory     |
| `process_network_receive_bytes_total`  | Process RX bytes           |
| `process_network_transmit_bytes_total` | Process TX bytes           |
| `process_start_time_seconds`           | Process start time         |
