// SPDX-FileCopyrightText:  Copyright Hewlett Packard Enterprise Development LP

package com.morpheusdata.dashboard

import com.morpheusdata.response.ServiceResponse
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import javax.net.ssl.HttpsURLConnection
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
        } catch (Exception e) {
            _log.get()?.error("Prometheus dashboard data fetch error: ${e.message}", e)
            return ServiceResponse.success(emptyDashboard())
        } finally {
            _cfg.remove()
            _log.remove()
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
}
