package com.morpheusdata.dashboard

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.dashboard.AbstractDashboardProvider
import com.morpheusdata.model.Dashboard
import com.morpheusdata.model.DashboardItem
import groovy.util.logging.Slf4j

/**
 * Registers the "Prometheus Dashboard" in Morpheus
 * (Administration → Settings → Dashboards to Display).
 *
 * It contains a single DashboardItem wired to the PrometheusDashboardItemProvider
 * widget type. Morpheus renders the item's templatePath + scriptPath, which
 * mounts the React widget that polls the JSON API and displays all charts.
 */
@Slf4j
class PrometheusDashboardProvider extends AbstractDashboardProvider {

    Plugin          plugin
    MorpheusContext morpheusContext

    PrometheusDashboardProvider(Plugin plugin, MorpheusContext context) {
        this.plugin          = plugin
        this.morpheusContext = context
    }

    @Override MorpheusContext getMorpheus() { morpheusContext }
    @Override Plugin getPlugin()            { plugin }
    @Override String getCode()              { 'prometheus-dashboard' }
    @Override String getName()              { 'Prometheus Dashboard' }

    @Override
    Dashboard getDashboard() {
        log.info("PrometheusDashboardProvider getDashboard() called")

        def rtn = new Dashboard()
        rtn.name             = getName()
        rtn.code             = getCode()
        rtn.dashboardId      = 'prometheusDashboard'
        rtn.category         = 'prometheus'
        rtn.title            = 'Prometheus Dashboard'
        rtn.description      = 'Full libvirt + Node Exporter Prometheus metrics on the Morpheus home dashboard.'
        rtn.defaultDashboard = true
        rtn.enabled          = true
        rtn.sourceType       = 'system'
        rtn.templatePath     = 'hbs/prometheus-dashboard'
        // scriptPath must be non-null — Morpheus calls hashCode() on it during
        // dashboard sync and NPEs if null. Point it at the placeholder asset.
        rtn.scriptPath       = 'prometheus-dashboard.js'

        def dashboardItems = []
        def itemType = getMorpheus().getDashboard()
                .getDashboardItemType('dashboard-item-prometheus').blockingGet()
        log.info("Prometheus item type lookup for 'dashboard-item-prometheus' -> ${itemType ? 'FOUND' : 'NULL'}")

        if (itemType) {
            def item = new DashboardItem()
            item.type       = itemType
            item.itemRow    = 0
            item.itemColumn = 0
            item.itemGroup  = 'main'
            item.groupRow   = 0
            dashboardItems << item
        } else {
            log.warn("Prometheus dashboard item type not found at registration time — item will be added on next restart")
        }

        rtn.dashboardItems = dashboardItems
        log.info("Prometheus dashboard built with ${dashboardItems.size()} item(s)")
        return rtn
    }
}
