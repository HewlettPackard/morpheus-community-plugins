// SPDX-FileCopyrightText:  Copyright Hewlett Packard Enterprise Development LP

package com.morpheusdata.dashboard

import com.morpheusdata.response.ServiceResponse

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.HttpURLConnection
import java.net.URL
import java.net.Proxy

/**
 * Shared Prometheus query logic for the Prometheus Dashboard plugin.
 *
 * Fetches libvirt VM metrics and node_exporter host metrics from Prometheus,
 * builds all SVG charts server-side, and returns a flat Map of SVG strings +
 * stat values ready for the React widget to consume as JSON.
 */
class PrometheusQueryHelper{

    // Holds per-request config (host/port/creds/node/job)
    // set by fetchLibvirtDashboardData and cleaned on exit
    private static final ThreadLocal<Map> _cfg = new ThreadLocal<>()
    // Request-scoped logger reference
    private static final ThreadLocal<Object> _log = new ThreadLocal<>()

    // Accessors for request-scoped settings
    private static String host() { _cfg.get()?.host }
    private static int port() { ((_cfg.get()?.port ?:9090) as int) }
    private static String user() { _cfg.get()?.user ?: 'admin' }
    private static String pass() { _cfg.get()?.pass }
    private static String node() { _cfg.get()?.node }
    private static String job() { _cfg.get()?.job ?: 'hvm-os' }

    static final List<String> PALETTE = [
        '#7EB26D', '#EAB839', '#6ED0E0', '#EF843C',
        '#E24D42', '#1F78C1', '#BA43A9', '#705DA0',
        '#508642', '#CCA300', '#447EBC', '#C15C17'
    ]

    // --External call surface----------------------------------------

    // Public entry point
    static ServiceResponse<Map<String, Object>> fetchLibvirtDashboardData(def log, String timeRange = '1h', Map cfg = [:]) {
        _cfg.set(cfg ?: [:])
        _log.set(log)
        try {
            // Return early if there's config missing
            List<String> missing = missingRequiredConfig()
            if (missing) {
                _log.get()?.warn("Prometheus dashboard config missing required fields: ${missing.join(', ')}. Returning empty dashboard.")
                return ServiceResponse.success(emptyDashboard())
            }

            disableSslVerification()
            
            long rangeSeconds
            int step

            switch (timeRange) {
                case '6h': rangeSeconds = 21600L; step = 300; break
                case '12h': rangeSeconds = 43200L; step = 600; break
                case '1d':  rangeSeconds = 86400L; step = 1200; break
                default:    rangeSeconds = 3600L;  step = 60       // '1h'
            }
            // Align 'now' to prevent end time drift across charts
            long now = (System.currentTimeMillis() / 1000L / step) * step
            long start = now - rangeSeconds
            
            // Row 1 - Instance Info
            List<Map> cpuSeries      = qr('rate(libvirt_domain_info_cpu_time_seconds_total[5m])', start, now, step)
            List<Map> memUsageSeries = qr('libvirt_domain_memory_stats_used_percent', start, now, step)
            List<Map> storageSeries  = qr('(libvirt_storage_pool_capacity_bytes - libvirt_storage_pool_available_bytes)/libvirt_storage_pool_capacity_bytes', start, now, step, 'storage_pool')
            List<Map> memAllocData   = qi('libvirt_domain_info_maximum_memory_bytes{}')
            List<Map> vcpuData       = qi('libvirt_domain_info_virtual_cpus{}')

            // Row 2 – Network Interface
            List<Map> netTxB  = sfx(qr('irate(libvirt_domain_interface_stats_transmit_bytes_total{}[5m])',  start, now, step), ' - transmit')
            List<Map> netRxB  = sfx(qr('irate(libvirt_domain_interface_stats_receive_bytes_total{}[5m])',   start, now, step), ' - receive')
            List<Map> netRxPk = sfx(qr('rate(libvirt_domain_interface_stats_receive_packets_total{}[5m])',  start, now, step), ' - receive')
            List<Map> netTxPk = sfx(qr('rate(libvirt_domain_interface_stats_transmit_packets_total{}[5m])', start, now, step), ' - transmit')
            List<Map> netTxDr = sfx(qr('rate(libvirt_domain_interface_stats_transmit_drops_total{}[5m])',   start, now, step), ' - transmit')
            List<Map> netRxDr = sfx(qr('rate(libvirt_domain_interface_stats_receive_drops_total{}[5m])',    start, now, step), ' - receive')
            List<Map> netRxEr = sfx(qr('rate(libvirt_domain_interface_stats_receive_errors_total{}[5m])',   start, now, step), ' - receive')
            List<Map> netTxEr = sfx(qr('rate(libvirt_domain_interface_stats_transmit_errors_total{}[5m])',  start, now, step), ' - transmit')

            // Row 3 – Block / Volumes
            List<Map> blkWrRq = sfx(qr('irate(libvirt_domain_block_stats_write_requests_total{}[5m])', start, now, step), ' - write')
            List<Map> blkRdRq = sfx(qr('irate(libvirt_domain_block_stats_read_requests_total{}[5m])',  start, now, step), ' - read')
            List<Map> blkWrBy = sfx(qr('irate(libvirt_domain_block_stats_write_bytes_total{}[5m])',    start, now, step), ' - write')
            List<Map> blkRdBy = sfx(qr('irate(libvirt_domain_block_stats_read_bytes_total{}[5m])',     start, now, step), ' - read')

            Map<String, Object> data = [
                timestamp        : new Date().format('yyyy-MM-dd HH:mm:ss'),
                timeRange        : timeRange,
                // Row 1
                cpuChartSvg      : buildMultiLineSvg(cpuSeries,      'percentunit'),
                memUsageChartSvg : buildMultiLineSvg(memUsageSeries,  'percent'),
                storageChartSvg  : buildMultiLineSvg(storageSeries,   'percentunit'),
                memAllocatedSvg  : buildBarSvg(memAllocData, 'bytes'),
                vcpuSvg          : buildBarSvg(vcpuData,     'short'),
                // Row 2
                netTrafficSvg    : buildMultiLineSvg(netTxB + netRxB,   'bytes'),
                netPacketsSvg    : buildMultiLineSvg(netRxPk + netTxPk, 'short'),
                netDropsSvg      : buildMultiLineSvg(netTxDr + netRxDr, 'short'),
                netErrorsSvg     : buildMultiLineSvg(netRxEr + netTxEr, 'short'),
                // Row 3
                blockReqSvg      : buildMultiLineSvg(blkWrRq + blkRdRq, 'short'),
                blockBytesSvg    : buildMultiLineSvg(blkWrBy + blkRdBy, 'binBps'),
            ] as Map<String, Object>

            // Row 4 – Node Exporter host metrics
            data.putAll(fetchNodeExporterSection(now, start, step))

            return ServiceResponse.success(data)
        } catch (Exception e) {
            _log.get()?.error("Prometheus dashboard data fetch error: ${e.message}", e)
            return ServiceResponse.success(emptyDashboard())
        } finally {
            _cfg.remove()
            _log.remove()
        }
    }

    // Dashboard section builders
    private static Map<String, Object> fetchNodeExporterSection(long now, long chartStart, int chartStep) {
        try {
            double uptime     = qiSingle("node_time_seconds{instance=\"${node()}\",job=\"${job()}\"} - node_boot_time_seconds{instance=\"${node()}\",job=\"${job()}\"}") ?: 0.0
            double cpuCores   = qiSingle("count(count(node_cpu_seconds_total{instance=\"${node()}\",job=\"${job()}\"}) by (cpu))") ?: 0.0
            double cpuBusy    = qiSingle("(sum by(instance) (irate(node_cpu_seconds_total{instance=\"${node()}\",job=\"${job()}\", mode!=\"idle\"}[5m])) / on(instance) group_left sum by (instance)((irate(node_cpu_seconds_total{instance=\"${node()}\",job=\"${job()}\"}[5m])))) * 100") ?: 0.0
            double load5m     = qiSingle("avg(node_load5{instance=\"${node()}\",job=\"${job()}\"}) / count(count(node_cpu_seconds_total{instance=\"${node()}\",job=\"${job()}\"}) by (cpu)) * 100") ?: 0.0
            double load15m    = qiSingle("avg(node_load15{instance=\"${node()}\",job=\"${job()}\"}) / count(count(node_cpu_seconds_total{instance=\"${node()}\",job=\"${job()}\"}) by (cpu)) * 100") ?: 0.0
            double rootFsSize = qiSingle("node_filesystem_size_bytes{instance=\"${node()}\",job=\"${job()}\",mountpoint=\"/\",fstype!=\"rootfs\"}") ?: 0.0
            double rootFsUsed = qiSingle("100 - ((node_filesystem_avail_bytes{instance=\"${node()}\",job=\"${job()}\",mountpoint=\"/\",fstype!=\"rootfs\"} * 100) / node_filesystem_size_bytes{instance=\"${node()}\",job=\"${job()}\",mountpoint=\"/\",fstype!=\"rootfs\"})") ?: 0.0
            double ramSize    = qiSingle("node_memory_MemTotal_bytes{instance=\"${node()}\",job=\"${job()}\"}") ?: 0.0
            double ramUsed    = qiSingle("100 - ((node_memory_MemAvailable_bytes{instance=\"${node()}\",job=\"${job()}\"} * 100) / node_memory_MemTotal_bytes{instance=\"${node()}\",job=\"${job()}\"})") ?: 0.0
            double swapSize   = qiSingle("node_memory_SwapTotal_bytes{instance=\"${node()}\",job=\"${job()}\"}") ?: 0.0
            double swapUsed   = qiSingle("((node_memory_SwapTotal_bytes{instance=\"${node()}\",job=\"${job()}\"} - node_memory_SwapFree_bytes{instance=\"${node()}\",job=\"${job()}\"}) / (node_memory_SwapTotal_bytes{instance=\"${node()}\",job=\"${job()}\"})) * 100") ?: 0.0
            def cpuChart = buildMultiLineSvg([
                [label: 'system', points: qrSingle("sum by(instance) (irate(node_cpu_seconds_total{instance=\"${node()}\",job=\"${job()}\", mode=\"system\"}[5m])) / on(instance) group_left sum by (instance)((irate(node_cpu_seconds_total{instance=\"${node()}\",job=\"${job()}\"}[5m])))", chartStart, now, chartStep)],
                [label: 'user',   points: qrSingle("sum by(instance) (irate(node_cpu_seconds_total{instance=\"${node()}\",job=\"${job()}\", mode=\"user\"}[5m])) / on(instance) group_left sum by (instance)((irate(node_cpu_seconds_total{instance=\"${node()}\",job=\"${job()}\"}[5m])))", chartStart, now, chartStep)],
                [label: 'iowait', points: qrSingle("sum by(instance) (irate(node_cpu_seconds_total{instance=\"${node()}\",job=\"${job()}\", mode=\"iowait\"}[5m])) / on(instance) group_left sum by (instance)((irate(node_cpu_seconds_total{instance=\"${node()}\",job=\"${job()}\"}[5m])))", chartStart, now, chartStep)],
                [label: 'idle',   points: qrSingle("sum by(instance) (irate(node_cpu_seconds_total{instance=\"${node()}\",job=\"${job()}\", mode=\"idle\"}[5m])) / on(instance) group_left sum by (instance)((irate(node_cpu_seconds_total{instance=\"${node()}\",job=\"${job()}\"}[5m])))", chartStart, now, chartStep)]
            ], 'percentunit')

            def memChart = buildMultiLineSvg([
                [label: 'total', points: qrSingle("node_memory_MemTotal_bytes{instance=\"${node()}\",job=\"${job()}\"}", chartStart, now, chartStep)],
                [label: 'used',  points: qrSingle("node_memory_MemTotal_bytes{instance=\"${node()}\",job=\"${job()}\"} - node_memory_MemFree_bytes{instance=\"${node()}\",job=\"${job()}\"} - (node_memory_Cached_bytes{instance=\"${node()}\",job=\"${job()}\"} + node_memory_Buffers_bytes{instance=\"${node()}\",job=\"${job()}\"} + node_memory_SReclaimable_bytes{instance=\"${node()}\",job=\"${job()}\"})", chartStart, now, chartStep)],
                [label: 'cache', points: qrSingle("node_memory_Cached_bytes{instance=\"${node()}\",job=\"${job()}\"} + node_memory_Buffers_bytes{instance=\"${node()}\",job=\"${job()}\"} + node_memory_SReclaimable_bytes{instance=\"${node()}\",job=\"${job()}\"}", chartStart, now, chartStep)],
                [label: 'free',  points: qrSingle("node_memory_MemFree_bytes{instance=\"${node()}\",job=\"${job()}\"}", chartStart, now, chartStep)]
            ], 'bytes')

            def netChart = buildMultiLineSvg([
                [label: 'receive (bits/s)',  points: qrSingle("irate(node_network_receive_bytes_total{instance=\"${node()}\",job=\"${job()}\"}[5m])*8", chartStart, now, chartStep)],
                [label: 'transmit (bits/s)', points: qrSingle("irate(node_network_transmit_bytes_total{instance=\"${node()}\",job=\"${job()}\"}[5m])*8", chartStart, now, chartStep)]
            ], 'short')

            def diskChart = buildMultiLineSvg([
                [label: 'disk used %', points: qrSingle("100 - ((node_filesystem_avail_bytes{instance=\"${node()}\",job=\"${job()}\",device!~'rootfs'} * 100) / node_filesystem_size_bytes{instance=\"${node()}\",job=\"${job()}\",device!~'rootfs'})", chartStart, now, chartStep)]
            ], 'percent')

            return [
                nodeUptime        : formatUptime(uptime as long),
                nodeUptimeClass   : '',
                nodeCpuCores      : String.format('%.0f', cpuCores),
                nodeCpuBusy       : String.format('%.1f', cpuBusy),
                nodeCpuBusyClass  : cssClass(cpuBusy, 85, 95),
                nodeSysLoad5m     : String.format('%.1f', load5m),
                nodeSysLoad5mClass: cssClass(load5m, 85, 95),
                nodeSysLoad15m    : String.format('%.1f', load15m),
                nodeSysLoad15mClass: cssClass(load15m, 85, 95),
                nodeRootFsTotal   : formatBytesAlt(rootFsSize as long),
                nodeRootFsUsed    : String.format('%.1f', rootFsUsed),
                nodeRootFsUsedClass: cssClass(rootFsUsed, 80, 90),
                nodeRamTotal      : formatBytesAlt(ramSize as long),
                nodeRamUsed       : String.format('%.1f', ramUsed),
                nodeRamUsedClass  : cssClass(ramUsed, 80, 90),
                nodeSwapTotal     : formatBytesAlt(swapSize as long),
                nodeSwapUsed      : String.format('%.1f', swapUsed),
                nodeSwapUsedClass : cssClass(swapUsed, 10, 25),
                nodeCpuChartSvg   : cpuChart,
                nodeMemChartSvg   : memChart,
                nodeNetChartSvg   : netChart,
                nodeDiskChartSvg  : diskChart,
                nodeInstance      : node() ?: 'N/A',
                nodeJob           : job() ?: 'N/A',
            ]
        } catch (Exception e) {
            _log.get()?.warn("Node exporter section failed (instance=${node()}, job=${job()}): ${e.message}")
            return emptyNodeSection()
        }
    }

    // --Prometheus query helpers----------------------------------------

    // Runs instant query
    // Returns [{label, value}]
    private static List<Map> qi(String promql, String labelField = 'domain') {
        try {
            String enc = URLEncoder.encode(promql, 'UTF-8')
            String url = "http://${host()}:${port()}/api/v1/query?query=${enc}"
            def resp = httpGet(url)
            return (resp?.data?.result ?: []).collect { series ->
                String label = series.metric?."${labelField}" ?: (series.metric?.toString() ?: 'unknown')
                double value = (series.value[1] as String).toDouble()
                [label: label, value: value]
            }
        } catch (Exception e) {
            _log.get()?.warn("Prometheus instant query failed: ${e.message}")
            return []
        }
    }

    // Runs instant query
    // Returns value
    private static Double qiSingle(String promql) {
        try {
            String enc = URLEncoder.encode(promql, 'UTF-8')
            String url = "http://${host()}:${port()}/api/v1/query?query=${enc}"
            def resp = httpGet(url)
            def result = resp?.data?.result
            if (result && result.size() > 0 && result[0]?.value)
                return (result[0].value[1] as String).toDouble()
        } catch (Exception e) {
            _log.get().warn("Prometheus range query failed: ${e.message}")
            return null
        }
    }

    // Runs range query
    // Returns [{label, points: [{ts: epoch, timestamp: HH:mm, value: val}]}]
    private static List<Map> qr(String promql, long start, long end, int step, String labelField = 'domain') {
        try {
            String enc = URLEncoder.encode(promql, 'UTF-8')
            String url = "http://${host()}:${port()}/api/v1/query_range?query=${enc}&start=${start}&end=${end}&step=${step}"
            def resp = httpGet(url)
            return (resp?.data?.result ?: []).collect{ series ->
                String label = series.metric?."${labelField}" ?: (series.metric?.toString() ?: 'unknown')
                List pts = (series.values ?: [])
                    .findAll { pt -> !(pt[1] as String).equalsIgnoreCase('NaN') }
                    .collect { pt -> [ts: pt[0] as long, timestamp: new Date((pt[0] as long) * 1000L).format('HH:mm'), value: (pt[1] as String).toDouble()]
                }
                [label: label, points: pts]

            }
        } catch (Exception e) { 
            _log.get().warn("Prometheus range query failed: ${e.message}")
            return []
        }
    }

    // Runs range query
    // Returns [{ts: epoch, timestamp: HH:mm, value: val}]
    private static List<Map> qrSingle(String promql, long start, long end, int step) {
        try {
            String enc = URLEncoder.encode(promql, 'UTF-8')
            String url = "http://${host()}:${port()}/api/v1/query_range?query=${enc}&start=${start}&end=${end}&step=${step}"
            def resp = httpGet(url)
            def result = resp?.data?.result
            if (result && result.size() > 0 && result[0]?.values) {
                return result[0].values
                    .findAll { pt -> !(pt[1] as String).equalsIgnoreCase('NaN') }
                    .collect { pt -> [ts: pt[0] as long, timestamp: new Date((pt[0] as long) * 1000L).format('HH:mm'), value: (pt[1] as String).toDouble()]
                }
            }
        } catch (Exception e) { 
            _log.get().warn("Prometheus range query failed: ${e.message}")
            return []
        }
    }

    // Append a label suffix to each series
    private static List<Map> sfx(List<Map> series, String suffix) {
        series.collect { s -> [label: "${s.label}${suffix}", points: s.points] }
    }

    // --HTTP helper----------------------------------------
    private static def httpGet(String url){
        HttpURLConnection conn
        try{
            conn = new URL(url).openConnection(Proxy.NO_PROXY) as HttpURLConnection
            conn.setRequestMethod('GET')
            conn.setConnectTimeout(8_000)
            conn.setReadTimeout(8_000)
            conn.setRequestProperty('Authorization', 'Basic ' + Base64.encoder.encodeToString("${user()}:${pass()}".getBytes('UTF-8')))
            conn.connect()
            
            if (conn.responseCode == 200) return new JsonSlurper().parse(conn.inputStream)
            
            _log.get()?.warn("Prometheus HTTP ${conn.responseCode} for ${url}")
            return null
        } catch (Exception e) {
            _log.get()?.warn("Prometheus HTTP request failed: ${e.message}")
            return null  
        } finally {
            conn?.disconnect()
        }
    }

    // --Fallback helpers----------------------------------------
    private static List<String> missingRequiredConfig() {
        List<String> missing = []
        if (!host()?.trim()) missing << 'host'
        if (!node()?.trim()) missing << 'node'
        return missing
    }

    private static Map<String, Object> emptyNodeSection() {
        String e = noDataSvg()
        [
            nodeUptime: 'N/A', nodeUptimeClass: '', nodeCpuCores: 'N/A',
            nodeCpuBusy: 'N/A', nodeCpuBusyClass: '',
            nodeSysLoad5m: 'N/A', nodeSysLoad5mClass: '',
            nodeSysLoad15m: 'N/A', nodeSysLoad15mClass: '',
            nodeRootFsTotal: 'N/A', nodeRootFsUsed: 'N/A', nodeRootFsUsedClass: '',
            nodeRamTotal: 'N/A', nodeRamUsed: 'N/A', nodeRamUsedClass: '',
            nodeSwapTotal: 'N/A', nodeSwapUsed: 'N/A', nodeSwapUsedClass: '',
            nodeCpuChartSvg: e, nodeMemChartSvg: e, nodeNetChartSvg: e, nodeDiskChartSvg: e,
            nodeInstance: node() ?: 'N/A', nodeJob: job(),
        ]
    }

    private static Map<String, Object> emptyDashboard() {
        String e = noDataSvg()
        [
            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
            cpuChartSvg: e, memUsageChartSvg: e, storageChartSvg: e,
            memAllocatedSvg: e, vcpuSvg: e,
            netTrafficSvg: e, netPacketsSvg: e, netDropsSvg: e, netErrorsSvg: e,
            blockReqSvg: e, blockBytesSvg: e,
        ] + emptyNodeSection()
    }

    private static String noDataSvg() {
        '<svg xmlns="http://www.w3.org/2000/svg" width="100%" viewBox="0 0 1100 180">' +
        '<rect width="1100" height="180" fill="#f8f9fa" rx="4"/>' +
        '<text x="550" y="96" text-anchor="middle" fill="#adb5bd" font-family="sans-serif" font-size="15">No data</text>' +
        '</svg>'
    }

    // --SVG chart builders----------------------------------------

    // Multi-series line chart
    static String buildMultiLineSvg(List<Map> seriesList, String unit = '') {
        List<Map> active = seriesList.findAll { it.points && (it.points as List).size() >= 2 }
        if (!active) return noDataSvg()

        int svgW = 1100, chartH = 270
        int padL = 110, padR = 20, padT = 20, padB = 48
        int plotW = svgW - padL - padR
        int plotH = chartH - padT - padB
        int plotBottom = padT + plotH
        int plotRight  = padL + plotW

        int legendRows = (active.size() + 2).intdiv(3)
        int legendH    = legendRows * 28 + 14
        int svgH       = chartH + legendH

        double minVal = active.collect { s -> (s.points as List).collect { it.value as double }.min() as double }.min()
        double maxVal = active.collect { s -> (s.points as List).collect { it.value as double }.max() as double }.max()
        if (maxVal == minVal) { minVal -= 0.5; maxVal += 0.5 }
        double valRange = maxVal - minVal

        long minTs = active.collect { s -> (s.points as List).collect { it.ts as long }.min() as long }.min()
        long maxTs = active.collect { s -> (s.points as List).collect { it.ts as long }.max() as long }.max()
        if (maxTs == minTs) maxTs = minTs + 1
        long tsRange = maxTs - minTs

        def sb = new StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100%\" viewBox=\"0 0 ${svgW} ${svgH}\" font-family=\"-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif\">\n")
        String seriesJson = JsonOutput.toJson(active.collect { s -> s.label as String })
        String descContent = seriesJson.replace('&', '&amp;').replace('<', '&lt;')
        sb.append("  <desc class=\"prom-sl\">${descContent}</desc>\n")
        sb.append("  <rect width=\"${svgW}\" height=\"${svgH}\" fill=\"white\"/>\n")
        sb.append("  <rect x=\"${padL}\" y=\"${padT}\" width=\"${plotW}\" height=\"${plotH}\" fill=\"#f8f9fa\" rx=\"2\"/>\n")

        // Y gridlines + labels
        (0..4).each { k ->
            double val = minVal + k * valRange / 4.0
            double y   = padT + plotH * (1.0 - k / 4.0)
            sb.append("  <line x1=\"${padL}\" y1=\"${String.format('%.1f', y)}\" x2=\"${plotRight}\" y2=\"${String.format('%.1f', y)}\" stroke=\"#dee2e6\" stroke-width=\"1\"/>\n")
            sb.append("  <text x=\"${padL - 6}\" y=\"${String.format('%.1f', y + 6.0)}\" text-anchor=\"end\" font-size=\"16\" fill=\"#6c757d\">${fmtVal(val, unit)}</text>\n")
        }
        sb.append("  <rect x=\"${padL}\" y=\"${padT}\" width=\"${plotW}\" height=\"${plotH}\" fill=\"none\" stroke=\"#dee2e6\" stroke-width=\"1\"/>\n")

        // Series polylines + dots
        active.eachWithIndex { series, idx ->
            String color = PALETTE[idx % PALETTE.size()]
            String sgAttr = escapeXml(series.label as String)
            sb.append("  <g class=\"sg sg-${idx}\">\n")
            String pts = (series.points as List).collect { pt ->
                double x = padL + ((pt.ts as long - minTs) / (double) tsRange) * plotW
                double y = padT + plotH - ((pt.value as double - minVal) / valRange) * plotH
                "${String.format('%.1f', x)},${String.format('%.1f', y)}"
            }.join(' ')
            sb.append("    <polyline points=\"${pts}\" fill=\"none\" stroke=\"${color}\" stroke-width=\"2\" stroke-linejoin=\"round\"/>\n")
            (series.points as List).each { pt ->
                double x = padL + ((pt.ts as long - minTs) / (double) tsRange) * plotW
                double y = padT + plotH - ((pt.value as double - minVal) / valRange) * plotH
                String dispVal = fmtVal(pt.value as double, unit)
                sb.append("    <circle cx=\"${String.format('%.1f', x)}\" cy=\"${String.format('%.1f', y)}\" r=\"3\" fill=\"${color}\" stroke=\"${color}\" stroke-opacity=\"0\" stroke-width=\"10\" class=\"prom-dot\" data-label=\"${sgAttr}\" data-ts=\"${pt.timestamp}\" data-val=\"${dispVal}\" opacity=\"0.7\"><title>${sgAttr}  ${pt.timestamp}  \u25ba  ${dispVal}</title></circle>\n")
            }
            sb.append("  </g>\n")
        }

        // X-axis time labels
        int xLbls = 8
        (0..<xLbls).each { i ->
            long ts  = minTs + (long)(i * tsRange / (xLbls - 1.0))
            double x = padL + (ts - minTs) / (double) tsRange * plotW
            String anc = (i == xLbls - 1) ? 'end' : 'middle'
            sb.append("  <text x=\"${String.format('%.1f', x)}\" y=\"${plotBottom + 22}\" text-anchor=\"${anc}\" font-size=\"16\" fill=\"#6c757d\">${new Date(ts * 1000L).format('HH:mm')}</text>\n")
        }

        // Legend (3 columns below chart)
        int legendY = chartH + 10
        active.eachWithIndex { series, idx ->
            double lx = padL + (idx % 3) * (plotW / 3.0)
            double ly = legendY + idx.intdiv(3) * 28
            String color = PALETTE[idx % PALETTE.size()]
            sb.append("  <g class=\"sg sg-${idx}\">\n")
            sb.append("    <line x1=\"${String.format('%.1f', lx)}\" y1=\"${String.format('%.1f', ly + 10)}\" x2=\"${String.format('%.1f', lx + 22)}\" y2=\"${String.format('%.1f', ly + 10)}\" stroke=\"${color}\" stroke-width=\"3\"/>\n")
            sb.append("    <text x=\"${String.format('%.1f', lx + 28)}\" y=\"${String.format('%.1f', ly + 15)}\" font-size=\"15\" fill=\"#333\">${escapeXml(truncate(series.label as String, 28))}</text>\n")
            sb.append("  </g>\n")
        }

        sb.append('</svg>')
        return sb.toString()
    }

    // Horizontal bar chart for instant-value
    static String buildBarSvg(List<Map> data, String unit = '') {
        if (!data) return noDataSvg()
        int barH = 36, padV = 10, padLeft = 20, labelW = 220, valueW = 130
        int barAreaW = 1100 - padLeft - labelW - valueW
        int svgH = data.size() * (barH + padV) + 46
        double maxVal = data.collect { it.value as double }.max() ?: 1.0

        def sb = new StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100%\" viewBox=\"0 0 1100 ${svgH}\" font-family=\"-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif\">\n")
        sb.append("  <rect width=\"1100\" height=\"${svgH}\" fill=\"white\"/>\n")

        data.eachWithIndex { item, i ->
            double val   = item.value as double
            double barW  = maxVal > 0 ? (val / maxVal) * barAreaW : 0
            int    y     = 20 + i * (barH + padV)
            String color = PALETTE[i % PALETTE.size()]
            sb.append("  <text x=\"${padLeft + labelW - 5}\" y=\"${y + 25}\" text-anchor=\"end\" font-size=\"16\" fill=\"#495057\">${escapeXml(truncate(item.label as String, 24))}</text>\n")
            sb.append("  <rect x=\"${padLeft + labelW}\" y=\"${y}\" width=\"${String.format('%.1f', barW)}\" height=\"${barH}\" fill=\"${color}\" rx=\"4\"/>\n")
            sb.append("  <text x=\"${String.format('%.1f', padLeft + labelW + barW + 8)}\" y=\"${y + 25}\" font-size=\"16\" fill=\"#333\">${fmtVal(val, unit)}</text>\n")
            sb.append("  <rect x=\"${padLeft}\" y=\"${y - 2}\" width=\"1100\" height=\"${barH + 4}\" fill=\"transparent\" class=\"prom-bar-hit\" data-label=\"${escapeXml(truncate(item.label as String, 24))}\" data-val=\"${fmtVal(val, unit)}\" data-color=\"${color}\"><title>${escapeXml(truncate(item.label as String, 24))}: ${fmtVal(val, unit)}</title></rect>\n")
        }

        sb.append('</svg>')
        return sb.toString()
    }

    // --Formatting helpers----------------------------------------
    static String fmtVal(double val, String unit) {
        switch (unit) {
            case 'bytes':       return formatBytes((long) val)
            case 'binBps':      return formatBytes((long) val) + '/s'
            case 'percent':     return String.format('%.1f%%', val)
            case 'percentunit': return String.format('%.1f%%', val * 100.0)
            case 'short':       return formatShort(val)
            default:            return String.format('%.2f', val)
        }
    }

    static String formatBytes(long bytes) {
        if (bytes < 0) bytes = 0
        if (bytes >= 1_000_000_000L) return String.format('%.1fG', bytes / 1_000_000_000.0)
        if (bytes >= 1_000_000L)     return String.format('%.1fM', bytes / 1_000_000.0)
        if (bytes >= 1_000L)         return String.format('%.1fK', bytes / 1_000.0)
        return "${bytes}"
    }

    static String formatShort(double val) {
        if (val >= 1_000_000.0) return String.format('%.1fM', val / 1_000_000.0)
        if (val >= 1_000.0)     return String.format('%.1fK', val / 1_000.0)
        return String.format('%.2f', val)
    }

    static String formatUptime(long seconds) {
        if (seconds <= 0) return '0:00:00'
        long days  = seconds / 86400L
        long hours = (seconds % 86400L) / 3600L
        long mins  = (seconds % 3600L) / 60L
        return "${days}:${String.format('%02d', hours)}:${String.format('%02d', mins)}"
    }

    static String formatBytesAlt(long b) {
        if (b < 0) b = 0
        if (b >= 1_000_000_000L) return String.format('%.1f GB', b / 1_000_000_000.0)
        if (b >= 1_000_000L)     return String.format('%.1f MB', b / 1_000_000.0)
        if (b >= 1_000L)         return String.format('%.1f KB', b / 1_000.0)
        return "${b} B"
    }

    private static String truncate(String s, int max) {
        s.length() > max ? s.substring(0, max - 1) + '\u2026' : s
    }

    private static String escapeXml(String s) {
        s?.replace('&', '&amp;')?.replace('<', '&lt;')?.replace('>', '&gt;')?.replace('"', '&quot;') ?: ''
    }

    private static String cssClass(double val, double warn, double danger) {
        if (val >= danger) return 'danger'
        if (val >= warn)   return 'warning'
        return ''
    }

    // --Environment/security helper----------------------------------------
    private static void disableSslVerification(){
        def trustAll = [new javax.net.ssl.X509TrustManager() {
            java.security.cert.X509Certificate[] getAcceptedIssuers() { null }
            void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
            void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
        }] as javax.net.ssl.TrustManager[]
        def sc = javax.net.ssl.SSLContext.getInstance('SSL')
        sc.init(null, trustAll, new java.security.SecureRandom())
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier({ h, s -> true } as javax.net.ssl.HostnameVerifier)
    }
}
