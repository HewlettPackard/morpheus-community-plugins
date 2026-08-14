package com.morpheusdata.dashboard

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.dashboard.AbstractDashboardItemTypeProvider
import com.morpheusdata.model.DashboardItemType
import groovy.util.logging.Slf4j

/**
 * Dashboard item type that registers the Prometheus widget into the Morpheus
 * dashboard item catalogue.
 *
 * templatePath — HBS file that provides the React mount point div.
 * scriptPath   — compiled JSX asset (prometheus-dashboard-widget.js) that
 *                mounts the React component into that div.
 */
@Slf4j
class PrometheusDashboardItemProvider extends AbstractDashboardItemTypeProvider {

    Plugin          plugin
    MorpheusContext morpheusContext

    PrometheusDashboardItemProvider(Plugin plugin, MorpheusContext context) {
        this.plugin          = plugin
        this.morpheusContext = context
    }

    @Override MorpheusContext getMorpheus() { morpheusContext }
    @Override Plugin getPlugin()            { plugin }
    @Override String getCode()              { 'dashboard-item-prometheus' }
    @Override String getName()              { 'Prometheus Dashboard' }

    @Override
    DashboardItemType getDashboardItemType() {
        def rtn = new DashboardItemType()
        rtn.name         = getName()
        rtn.code         = getCode()
        rtn.category     = 'prometheus'
        rtn.title        = 'Prometheus Dashboard'
        rtn.description  = 'Full libvirt + Node Exporter metrics: CPU, memory, storage, network, block I/O and host system charts.'
        rtn.uiSize       = 'full'
        rtn.templatePath = 'hbs/prometheus-widget'
        rtn.scriptPath   = 'prometheus-dashboard-widget.js'
        // Use the same permission anchor the built-in cluster widgets use.
        rtn.permission = morpheusContext.getPermission().getByCode('infrastructure-cluster').blockingGet()
        rtn.setAccessTypes(['read', 'full'])
        return rtn
    }
}
