// SPDX-FileCopyrightText:  Copyright Hewlett Packard Enterprise Development LP

package com.morpheusdata.dashboard

import com.morpheusdata.response.ServiceResponse

import groovy.json.JsonSlurper
import javax.net.ssl.HttpsURLConnection
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

    static ServiceResponse<Map<String, Object>> fetchLibvirtDashboardData(def log, String timeRange = '1h', Map cfg = [:]) {
        _cfg.set(cfg ?: [:])
        _log.set(log)
        try {
            // Return early if there's config missing
            List<String> missing = missingRequiredConfig()
            if (missing) {
                _log.get()?.warn("Prometheus dashboard config missing required fields: ${missing.join(', ')}. Returing empty dashboard.")
                return ServiceResponse.success(emptyDashboard())
            }

            disableSslVerification()
            
            long rangeSeconds
            long step

            switch (timeRange) {
                case '6h': rangeSeconds = 21600L; step = 300; break
                case '12h': rangeSeconds = 43200L; step = 600; break
                case '1d':  rangeSeconds = 86400L; step = 1200; break
                default:    rangeSeconds = 3600L;  step = 60       // '1h'
            }
            // Align 'now' to prevent end time drift across charts
            long now = (System.currentTimeMillis() / 1000L / step) * step
            long start = now - rangeSeconds
            }
            
            // Row 1 - Instance Info
            List<Map> cpuSeries      = qr('rate(libvirt_domain_info_cpu_time_seconds_total[5m])', start, now, step)
            List<Map> memUsageSeries = qr('libvirt_domain_memory_stats_used_percent', start, now, step)
            List<Map> storageSeries  = qr('(libvirt_storage_pool_capacity_bytes - libvirt_storage_pool_available_bytes)/libvirt_storage_pool_capacity_bytes', start, now, step, 'storage_pool')

            Map<String, Object> data = [
                timestamp        : new Date().format('yyyy-MM-dd HH:mm:ss'),
                timeRange        : timeRange,
                // Row 1
                cpuChartSvg      : buildMultiLineSvg(cpuSeries,      'percentunit'),
                memUsageChartSvg : buildMultiLineSvg(memUsageSeries,  'percent'),
                storageChartSvg  : buildMultiLineSvg(storageSeries,   'percentunit'),
            ] as Map<String, Object>

            return ServiceResponse.success(data)
        
        } catch (Exception e) {
            _log.get()?.error("Prometheus dashboard data fetch error: ${e.message}", e)
            return ServiceResponse.success(emptyDashboard())
        } finally {
            _cfg.remove()
            _log.remove()
        }
    }

    // Prometheus query helpers

    // Runs range query
    // Returns [{label, points: [{ts: epoch, timestamp: HH:mm, value: val}]}]
    private static List<Map> qr(String promql, long start, long end, int step, String labelField = 'domain') {
        try {
            String enc = URLEncoder.encode(promql, 'UTF-8')
            String url = "https://${host()}:${port()}/api/v1/query_range?query=${enc}&start=${start}&end=${end}&step=${step}"
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

    private static def httpGet(String url){
        HttpsURLConnection conn
        try{
            conn = new URL(url).openConnection(Proxy.NO_PROXY) as HttpsURLConnection
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

    private static List<String> missingRequiredConfig() {
        List<String> missing = []
        if (!host()?.trim()) missing << 'host'
        if (!pass()?.trim()) missing << 'pass'
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
            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss')
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

    private static void disableSslVerification(){
        def trustAll = [new javax.net.ssl.X509TrustManager() {
            java.security.cert.X509Certificate[] getAcceptedIssuers() { null }
            void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
            void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
        }] as javax.net.ssl.TrustManager[]
        def sc = java.net.ssl.SSLContext.getInstance('SSL')
        sc.init(null, trustAll, new java.security.SecureRandom())
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier({ h, s -> true } as javax.net.ssl.HostnameVerifier)
    }
}
