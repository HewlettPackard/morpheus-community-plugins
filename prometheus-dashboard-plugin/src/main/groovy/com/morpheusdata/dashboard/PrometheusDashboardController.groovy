package com.morpheusdata.dashboard

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Permission
import com.morpheusdata.views.JsonResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.web.PluginController
import com.morpheusdata.web.Route
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

/**
 * JSON API controller for the Prometheus Dashboard widget.
 *
 * Each route fetches the full libvirt + node-exporter dashboard data for a
 * specific time range, builds all SVG charts server-side via PrometheusQueryHelper,
 * and returns the result as JSON. The React widget (prometheus-dashboard-widget.jsx)
 * polls these endpoints and injects the pre-rendered SVGs via dangerouslySetInnerHTML.
 *
 * Routes:
 *   GET /plugin/prometheusDashboard/api/data1h   — last 1 hour
 *   GET /plugin/prometheusDashboard/api/data6h   — last 6 hours
 *   GET /plugin/prometheusDashboard/api/data12h  — last 12 hours
 *   GET /plugin/prometheusDashboard/api/data1d   — last 24 hours
 */
@Slf4j
class PrometheusDashboardController implements PluginController {

    Plugin          plugin
    MorpheusContext morpheus

    PrometheusDashboardController(Plugin plugin, MorpheusContext morpheus) {
        this.plugin   = plugin
        this.morpheus = morpheus
    }

    @Override Plugin getPlugin()            { plugin }
    void setPlugin(Plugin p)                { this.plugin = p }
    @Override MorpheusContext getMorpheus() { morpheus }
    Boolean isEnabled()                     { true }
    String getCode()                        { 'prometheusDashboardController' }
    String getName()                        { 'Prometheus Dashboard Controller' }

    @Override
    List<Route> getRoutes() {
        def p = { String path, String action ->
            Route.build(path, action, Permission.build('admin-cm', 'full'))
        }
        [
            p('/prometheusDashboard/api/data1h',  'data1h'),
            p('/prometheusDashboard/api/data6h',  'data6h'),
            p('/prometheusDashboard/api/data12h', 'data12h'),
            p('/prometheusDashboard/api/data1d',  'data1d'),
        ]
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    /** Read plugin settings stored in Morpheus DB; fall back to built-in defaults. */
    private Map getPrometheusConfig() {
        Map s = [:]
        try {
            String json = morpheus.getSettings(plugin).blockingGet()
            if (json) s = new JsonSlurper().parseText(json) as Map
        } catch (Exception ignored) {}
        [
            host: s.promHost     ?: '',
            port: (s.promPort    ?: '9090').toString().toInteger(),
            user: s.promUser     ?: '',
            pass: s.promPass     ?: '',
            node: s.nodeInstance ?: '',
            job:  s.nodeJob      ?: 'hvm-os',
        ]
    }

    private def fetchData(String range) {
        try {
            def result = PrometheusQueryHelper.fetchLibvirtDashboardData(log, range, getPrometheusConfig())
            return JsonResponse.of(result.data ?: [error: 'no data'])
        } catch (Exception e) {
            log.error("PrometheusDashboardController [${range}] error: ${e.message}", e)
            return JsonResponse.of([error: e.message])
        }
    }

    def data1h(ViewModel<Map> model)  { fetchData('1h')  }
    def data6h(ViewModel<Map> model)  { fetchData('6h')  }
    def data12h(ViewModel<Map> model) { fetchData('12h') }
    def data1d(ViewModel<Map> model)  { fetchData('1d')  }
}
