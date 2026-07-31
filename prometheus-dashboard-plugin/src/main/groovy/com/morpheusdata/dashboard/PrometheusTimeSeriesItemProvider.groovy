package com.morpheusdata.dashboard

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.AbstractAnalyticsProvider
import com.morpheusdata.model.User
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import javax.net.ssl.HttpsURLConnection
import java.net.Proxy

@Slf4j
class PrometheusTimeSeriesItemProvider extends AbstractAnalyticsProvider {

	Plugin plugin
	MorpheusContext morpheusContext

	private static final String PROMQL = 'node_network_receive_bytes_total{device=~"ens160"}'

	/** Read the values the user entered in Administration → Integrations → Plugins. */
	private Map getPrometheusConfig() {
		Map s = [:]
		try {
			String json = morpheusContext.getSettings(plugin).blockingGet()
			if (json) s = new JsonSlurper().parseText(json) as Map
		} catch (Exception ignored) {}
		[
			host: s.promHost ?: '',
			port: (s.promPort ?: '9090').toString().toInteger(),
			user: s.promUser ?: '',
			pass: s.promPass ?: '',
		]
	}

	PrometheusTimeSeriesItemProvider(Plugin plugin, MorpheusContext context) {
		this.plugin = plugin
		this.morpheusContext = context
	}

	@Override MorpheusContext getMorpheus() { morpheusContext }
	@Override Plugin getPlugin() { plugin }
	@Override String getCode() { 'prometheus-timeseries-widget' }
	@Override String getName() { 'Prometheus Time Series' }
	@Override String getCategory() { 'prometheus' }
	@Override String getDescription() { 'One-hour trend of network receive bytes for ens160' }
	@Override Boolean getMasterTenantOnly() { false }
	@Override Boolean getSubTenantOnly() { false }
	@Override Integer getDisplayOrder() { 20 }

	@Override
	ServiceResponse<Map<String, Object>> loadData(User user, Map<String, Object> params) {
		try {
			def cfg = getPrometheusConfig()
			disableSslVerification()
			long now   = System.currentTimeMillis() / 1000L
			long start = now - 3600L
			String enc = URLEncoder.encode(PROMQL, 'UTF-8')
			String url = "https://${cfg.host}:${cfg.port}/api/v1/query_range?query=${enc}&start=${start}&end=${now}&step=30"
			HttpsURLConnection conn = new URL(url).openConnection(Proxy.NO_PROXY) as HttpsURLConnection
			conn.setRequestMethod('GET')
			conn.setConnectTimeout(10_000)
			conn.setReadTimeout(10_000)
			conn.setRequestProperty('Authorization', 'Basic ' + Base64.encoder.encodeToString("${cfg.user}:${cfg.pass}".getBytes('UTF-8')))
			conn.connect()
			if (conn.responseCode == 200) {
				def resp   = new JsonSlurper().parse(conn.inputStream)
				def result = resp?.data?.result
				def values = (result?.size() > 0) ? (result[0]?.values ?: []) : []
				List<Map> points = values.collect { pt ->
					[timestamp: new Date((pt[0] as long) * 1000L).format('HH:mm'), value: pt[1] as Double]
				}
				double currentValue = points ? (points[-1].value as double) : 0.0
				return ServiceResponse.success([svgChart: buildSvgChart(points), currentValue: formatBytes(currentValue), dataPoints: points.size()])
			}
			return ServiceResponse.success([svgChart: noDataSvg(), currentValue: 'N/A', dataPoints: 0])
		} catch (Exception e) {
			log.error("Prometheus time-series error: ${e.message}", e)
			return ServiceResponse.success([svgChart: noDataSvg(), currentValue: 'N/A', dataPoints: 0])
		}
	}

	@Override
	HTMLResponse renderTemplate(User user, Map<String, Object> data, Map<String, Object> params) {
		ViewModel<Map> model = new ViewModel<Map>()
		model.object = data
		getRenderer().renderTemplate('hbs/prometheus/prometheus-timeseries-widget', model)
	}

	private static String noDataSvg() {
		'<svg xmlns="http://www.w3.org/2000/svg" width="100%" viewBox="0 0 800 200"><rect width="800" height="200" fill="#f9fafb" rx="8"/><text x="400" y="105" text-anchor="middle" fill="#9ca3af" font-family="sans-serif" font-size="14">No data available</text></svg>'
	}

	private static String buildSvgChart(List<Map> points) {
		if (!points || points.size() < 2) return noDataSvg()
		int W = 800, H = 200, pL = 8, pR = 8, pT = 14, pB = 24
		int plotW = W - pL - pR
		int plotH = H - pT - pB
		int n = points.size()
		double minV = points.collect { it.value as double }.min()
		double maxV = points.collect { it.value as double }.max()
		if (maxV == minV) maxV = minV + 1.0
		double range = maxV - minV
		def xs = (0..<n).collect { i -> pL + (i / (n - 1.0)) * plotW }
		def ys = points.collect { pt -> pT + plotH - ((pt.value as double - minV) / range) * plotH }
		String linePts = (0..<n).collect { i -> "${String.format('%.1f', xs[i] as double)},${String.format('%.1f', ys[i] as double)}" }.join(' ')
		String areaPts = "${pL},${pT + plotH} ${linePts} ${W - pR},${pT + plotH}"
		String fmtMax  = formatBytes(maxV)
		String fmtMin  = formatBytes(minV)
		String lblFirst = points[0].timestamp
		String lblMid   = points[n.intdiv(2)].timestamp
		String lblLast  = points[-1].timestamp
		return """<svg xmlns="http://www.w3.org/2000/svg" width="100%" viewBox="0 0 ${W} ${H}" font-family="-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif">
  <rect width="${W}" height="${H}" fill="#f8f9fa" rx="8"/>
  <defs><linearGradient id="pg" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#7b2ff7" stop-opacity="0.25"/><stop offset="100%" stop-color="#7b2ff7" stop-opacity="0.02"/></linearGradient></defs>
  <polygon points="${areaPts}" fill="url(#pg)"/>
  <polyline points="${linePts}" fill="none" stroke="#7b2ff7" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>
  <text x="${pL + 2}" y="${pT + 10}" fill="#6b7280" font-size="10">${fmtMax}</text>
  <text x="${pL + 2}" y="${pT + plotH - 2}" fill="#6b7280" font-size="10">${fmtMin}</text>
  <text x="${pL}" y="${H - 4}" fill="#9ca3af" font-size="9" text-anchor="start">${lblFirst}</text>
  <text x="${W / 2}" y="${H - 4}" fill="#9ca3af" font-size="9" text-anchor="middle">${lblMid}</text>
  <text x="${W - pR}" y="${H - 4}" fill="#9ca3af" font-size="9" text-anchor="end">${lblLast}</text>
</svg>"""
	}

	private static String formatBytes(double bytes) {
		if (bytes >= 1e12) return String.format('%.2f TB', bytes / 1e12)
		if (bytes >= 1e9)  return String.format('%.2f GB', bytes / 1e9)
		if (bytes >= 1e6)  return String.format('%.2f MB', bytes / 1e6)
		if (bytes >= 1e3)  return String.format('%.2f KB', bytes / 1e3)
		return String.format('%.0f B', bytes)
	}

	private static void disableSslVerification() {
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
