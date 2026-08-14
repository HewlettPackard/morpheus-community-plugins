/**
 * Prometheus Dashboard widget — mounts on the Morpheus home dashboard grid.
 *
 * Uses the same host-provided globals as the built-in widgets:
 *   React, ReactDOM, $, Widget, WidgetHeader, EmptyWidget, LoadingWidget, Morpheus
 * (never imported — the dashboard page provides them at runtime).
 *
 * Data flow:
 *   1. componentDidMount → loadData('1h')
 *   2. Controller builds all SVGs server-side and returns JSON.
 *   3. SVGs are injected via dangerouslySetInnerHTML — zero client-side chart lib needed.
 *   4. Time-range buttons (1h / 6h / 12h / 1d) trigger a fresh fetch.
 *   5. After each render, initSeriesPickers() wires up the series filter picker
 *      on every multi-line SVG chart (search box + All/None toggles, state
 *      persisted in localStorage per chart key).
 */

// ── Series picker helpers (self-contained, no external deps) ─────────────────

var PICKER_STYLE_ID = 'prom-picker-style';

function ensurePickerStyles() {
  if (document.getElementById(PICKER_STYLE_ID)) return;
  var s = document.createElement('style');
  s.id = PICKER_STYLE_ID;
  s.textContent = [
    '.prom-picker{display:inline-block;position:relative;margin-bottom:6px}',
    '.prom-picker-btn{display:inline-flex;align-items:center;cursor:pointer;font-size:11px;font-weight:600;color:#495057;padding:4px 10px;background:#f0f2f5;border:1px solid #dee2e6;border-radius:5px;font-family:inherit;white-space:nowrap}',
    '.prom-picker-btn:hover{background:#e2e6ea;border-color:#ced4da}',
    '.prom-picker-panel{position:absolute;top:calc(100% + 4px);left:0;z-index:9999;background:#fff;border:1px solid #dee2e6;border-radius:6px;padding:8px;box-shadow:0 4px 16px rgba(0,0,0,0.18);min-width:220px;max-width:420px;max-height:260px;overflow-y:auto;display:grid;grid-template-columns:1fr 1fr;gap:1px}',
    '.prom-picker-search-wrap{grid-column:1/-1;margin-bottom:6px;padding-bottom:6px;border-bottom:1px solid #dee2e6}',
    '.prom-picker-search{width:100%;box-sizing:border-box;padding:4px 8px;font-size:11px;font-family:inherit;border:1px solid #dee2e6;border-radius:4px;outline:none;color:#495057}',
    '.prom-picker-search:focus{border-color:#1F78C1;box-shadow:0 0 0 2px rgba(31,120,193,0.18)}',
    '.prom-picker-actions{grid-column:1/-1;display:flex;gap:6px;margin-bottom:6px;padding-bottom:6px;border-bottom:1px solid #dee2e6}',
    '.prom-picker-all,.prom-picker-none{font-size:10px;padding:2px 8px;border:1px solid #dee2e6;border-radius:3px;background:#fff;cursor:pointer;color:#495057;font-family:inherit}',
    '.prom-picker-all:hover,.prom-picker-none:hover{background:#e9ecef}',
    '.prom-picker-label{display:flex;align-items:center;gap:5px;font-size:11px;color:#333;padding:3px 5px;cursor:pointer;border-radius:3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}',
    '.prom-picker-label:hover{background:#f0f2f5}',
    '.prom-picker-label input[type=checkbox]{flex-shrink:0;accent-color:#1F78C1}'
  ].join('\n');
  document.head.appendChild(s);
}

function htmlEsc(s) {
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

function toggleSg(svg, idx, visible) {
  svg.querySelectorAll('g.sg-' + idx).forEach(function(g) {
    g.style.display = visible ? '' : 'none';
  });
}

function updatePickerBtn(btn, checked, total) {
  btn.textContent = checked === total
    ? 'Filter series (' + total + ' shown) \u25BC'
    : 'Filter series (' + checked + '/' + total + ' shown) \u25BC';
}

function buildPickerFor(container, chartKey) {
  // Remove existing picker so re-renders don't double-up
  var old = container.querySelector('.prom-picker');
  if (old) old.parentNode.removeChild(old);

  var svg = container.querySelector('svg');
  if (!svg) return;
  var descEl = svg.querySelector('desc.prom-sl');
  if (!descEl) return;
  var labels;
  try { labels = JSON.parse(descEl.textContent); } catch(e) { return; }
  if (!labels || labels.length < 2) return;

  // Persist/restore hidden series in localStorage
  var storageKey = 'prom-sg-' + chartKey;
  var savedHidden = [];
  try { savedHidden = JSON.parse(localStorage.getItem(storageKey)) || []; } catch(e) {}

  function isHidden(l) { return savedHidden.indexOf(l) !== -1; }

  var rows = labels.map(function(label, idx) {
    return '<label class="prom-picker-label">'
      + '<input type="checkbox" class="prom-picker-cb"' + (isHidden(label) ? '' : ' checked')
      + ' data-idx="' + idx + '"> ' + htmlEsc(label) + '</label>';
  }).join('');

  var visCount = labels.filter(function(l){ return !isHidden(l); }).length;
  var wrap = document.createElement('div');
  wrap.className = 'prom-picker';
  wrap.innerHTML =
    '<button class="prom-picker-btn" type="button">Filter series ('
    + (visCount === labels.length ? labels.length : visCount + '/' + labels.length)
    + ' shown) \u25BC</button>'
    + '<div class="prom-picker-panel" style="display:none">'
    + '<div class="prom-picker-search-wrap"><input type="text" class="prom-picker-search" placeholder="Search series\u2026"></div>'
    + '<div class="prom-picker-actions"><button class="prom-picker-all" type="button">All</button><button class="prom-picker-none" type="button">None</button></div>'
    + rows + '</div>';

  // svg may be nested inside a dangerouslySetInnerHTML wrapper div — walk up to
  // the direct child of container before inserting, so insertBefore never throws.
  var insertRef = svg;
  while (insertRef.parentNode && insertRef.parentNode !== container) {
    insertRef = insertRef.parentNode;
  }
  container.insertBefore(wrap, insertRef);

  // Apply saved state immediately
  labels.forEach(function(label, idx) {
    if (isHidden(label)) toggleSg(svg, idx, false);
  });

  var btn   = wrap.querySelector('.prom-picker-btn');
  var panel = wrap.querySelector('.prom-picker-panel');
  var search = wrap.querySelector('.prom-picker-search');

  function countChecked() { return wrap.querySelectorAll('.prom-picker-cb:checked').length; }
  function saveState() {
    var hidden = [];
    wrap.querySelectorAll('.prom-picker-cb').forEach(function(cb) {
      if (!cb.checked) hidden.push(labels[parseInt(cb.getAttribute('data-idx'), 10)]);
    });
    try { localStorage.setItem(storageKey, JSON.stringify(hidden)); } catch(e) {}
  }
  function closePanel() {
    panel.style.display = 'none';
    search.value = '';
    wrap.querySelectorAll('.prom-picker-label').forEach(function(l){ l.style.display = ''; });
  }

  btn.addEventListener('click', function(e) {
    e.stopPropagation();
    if (panel.style.display !== 'none') { closePanel(); }
    else { panel.style.display = 'block'; search.focus(); }
    updatePickerBtn(btn, countChecked(), labels.length);
  });
  document.addEventListener('click', function(e) { if (!wrap.contains(e.target)) closePanel(); });
  search.addEventListener('keydown', function(e) { if (e.key === 'Escape') closePanel(); });
  search.addEventListener('input', function() {
    var q = search.value.toLowerCase();
    wrap.querySelectorAll('.prom-picker-label').forEach(function(lbl) {
      lbl.style.display = (q === '' || lbl.textContent.toLowerCase().indexOf(q) !== -1) ? '' : 'none';
    });
  });
  wrap.querySelector('.prom-picker-all').addEventListener('click', function() {
    wrap.querySelectorAll('.prom-picker-cb').forEach(function(cb) {
      cb.checked = true; toggleSg(svg, parseInt(cb.getAttribute('data-idx'), 10), true);
    });
    updatePickerBtn(btn, labels.length, labels.length); saveState();
  });
  wrap.querySelector('.prom-picker-none').addEventListener('click', function() {
    wrap.querySelectorAll('.prom-picker-cb').forEach(function(cb) {
      cb.checked = false; toggleSg(svg, parseInt(cb.getAttribute('data-idx'), 10), false);
    });
    updatePickerBtn(btn, 0, labels.length); saveState();
  });
  wrap.querySelectorAll('.prom-picker-cb').forEach(function(cb) {
    cb.addEventListener('change', function() {
      toggleSg(svg, parseInt(cb.getAttribute('data-idx'), 10), cb.checked);
      updatePickerBtn(btn, countChecked(), labels.length);
      saveState();
    });
  });
}

// Wire pickers on all svg panels inside a given root element
function initSeriesPickers(root) {
  ensurePickerStyles();
  (root || document).querySelectorAll('[data-prom-chart]').forEach(function(container) {
    buildPickerFor(container, container.getAttribute('data-prom-chart'));
  });
}

// ── React component ───────────────────────────────────────────────────────────

class PrometheusDashboardWidget extends React.Component {

  constructor(props) {
    super(props);
    this.state = {
      loaded:       false,
      loading:      false,
      range:        '1h',
      data:         null,
      error:        false,
      errorMessage: null
    };
    this.handleRefresh = this.handleRefresh.bind(this);
    this.rootRef = React.createRef();
  }

  componentDidMount() {
    this.loadData('1h');
    $(document).on('morpheus:refresh', this.handleRefresh);
    this._expandToFullWidth();
  }

  componentDidUpdate(prevProps, prevState) {
    // Re-init pickers whenever data or range changes (new SVGs injected)
    if (prevState.data !== this.state.data || prevState.range !== this.state.range) {
      if (this.rootRef.current) {
        setTimeout(function() { initSeriesPickers(this.rootRef.current); }.bind(this), 0);
      }
    }
  }

  /**
   * Walk up the DOM from the widget mount point and force every Bootstrap
   * column / Morpheus card wrapper to be full-width. This overrides the
   * fixed column widths and gutters the dashboard grid applies.
   */
  _expandToFullWidth() {
    try {
      var el = document.getElementById('prometheus-dashboard-widget');
      if (!el) return;
      var node = el.parentElement;
      var steps = 0;
      while (node && steps < 10) {
        var tag = (node.tagName || '').toLowerCase();
        var cls = node.className || '';
        // Target Bootstrap col-* grid cells and Morpheus widget card wrappers
        if (/\bcol(-\w+)?-\d+\b/.test(cls) ||
            /dashboard-item|widget-wrapper|widget-container|card-body|panel-body/.test(cls)) {
          node.style.setProperty('width',     '100%',  'important');
          node.style.setProperty('max-width', '100%',  'important');
          node.style.setProperty('flex',      '0 0 100%', 'important');
          node.style.setProperty('padding-left',  '0', 'important');
          node.style.setProperty('padding-right', '0', 'important');
        }
        // Stop at the dashboard row container — don't escape the page layout
        if (/\brow\b/.test(cls) || tag === 'main' || tag === 'section') break;
        node = node.parentElement;
        steps++;
      }
    } catch (e) {}
  }

  componentWillUnmount() {
    $(document).off('morpheus:refresh', this.handleRefresh);
  }

  handleRefresh() {
    // Only auto-refresh if not already loading.
    if (!this.state.loading) {
      this.loadData(this.state.range);
    }
  }

  loadData(range) {
    this.setState({ loading: true, error: false });
    fetch('/plugin/prometheusDashboard/api/data' + range, { credentials: 'same-origin' })
      .then(function(r) { return r.json(); })
      .then(function(d) {
        if (d && d.error) {
          this.setState({ loaded: true, loading: false, error: true, errorMessage: d.error, range: range });
        } else {
          this.setState({ loaded: true, loading: false, data: d, range: range, error: false });
        }
      }.bind(this))
      .catch(function(e) {
        this.setState({ loaded: true, loading: false, error: true, errorMessage: String(e), range: range });
      }.bind(this));
  }

  // ── Render helpers ────────────────────────────────────────────────────────

  svgPanel(title, desc, svgHtml, chartKey) {
    return (
      <div data-prom-chart={chartKey || ''} style={{marginBottom:'10px',padding:'0 12px'}}>
        <div style={{fontSize:'13px',fontWeight:600,marginBottom:'2px',color:'#2c3e50'}}>{title}</div>
        {desc
          ? <div style={{fontSize:'10px',color:'#adb5bd',marginBottom:'4px',fontFamily:'monospace'}}>{desc}</div>
          : null}
        <div dangerouslySetInnerHTML={{ __html: svgHtml || '' }} />
      </div>
    );
  }

  statCard(label, value, unit, cls) {
    var bg = cls === 'danger'  ? 'linear-gradient(135deg,#e74c3c,#c0392b)'
           : cls === 'warning' ? 'linear-gradient(135deg,#f39c12,#e67e22)'
           :                     'linear-gradient(135deg,#667eea,#764ba2)';
    return (
      <div style={{textAlign:'center',padding:'10px 8px',borderRadius:'6px',background:bg,color:'#fff'}}>
        <div style={{fontSize:'9px',textTransform:'uppercase',letterSpacing:'0.9px',opacity:0.85,marginBottom:'3px'}}>{label}</div>
        <div style={{fontSize:'16px',fontWeight:'bold',lineHeight:1.2}}>{value}</div>
        <div style={{fontSize:'9px',opacity:0.72,marginTop:'3px'}}>{unit}</div>
      </div>
    );
  }

  sectionLabel(text) {
    return (
      <div style={{fontSize:'11px',fontWeight:700,textTransform:'uppercase',letterSpacing:'0.8px',
                   color:'#495057',background:'#e9ecef',padding:'5px 12px',
                   marginBottom:'8px',marginTop:'4px'}}>
        {text}
      </div>
    );
  }

  // ── Main render ────────────────────────────────────────────────────────────

  render() {
    var loaded   = this.state.loaded;
    var loading  = this.state.loading;
    var range    = this.state.range;
    var data     = this.state.data || {};
    var error    = this.state.error;
    var errMsg   = this.state.errorMessage;

    var ranges = [
      { key: '1h',  label: '1h' },
      { key: '6h',  label: '6h' },
      { key: '12h', label: '12h' },
      { key: '1d',  label: '1d' }
    ];

    var btnBase   = { padding:'3px 11px', border:'1px solid #dee2e6', borderRadius:'4px',
                      cursor:'pointer', fontSize:'11px', fontFamily:'inherit',
                      transition:'background 0.1s, color 0.1s' };
    var btnActive = Object.assign({}, btnBase, { background:'#1F78C1', color:'#fff', borderColor:'#1F78C1' });
    var btnNormal = Object.assign({}, btnBase, { background:'#f8f9fa', color:'#495057' });

    return (
      <Widget>
        <WidgetHeader title="Prometheus — HPE VM Essentials" link="#" />

        <div className="dashboard-widget-content" ref={this.rootRef}>

          {/* ── Time range picker ── */}
          <div style={{padding:'6px 12px', borderBottom:'1px solid rgba(0,0,0,0.07)',
                       display:'flex', gap:'5px', alignItems:'center', flexWrap:'wrap'}}>
            <span style={{fontSize:'11px',color:'#6c757d',fontWeight:600,marginRight:'2px'}}>Range:</span>
            {ranges.map(function(r) {
              return (
                <button key={r.key}
                        style={range === r.key ? btnActive : btnNormal}
                        onClick={function() { this.loadData(r.key); }.bind(this)}>
                  {r.label}
                </button>
              );
            }.bind(this))}
            {loading
              ? <span style={{fontSize:'11px',color:'#adb5bd',marginLeft:'6px'}}>Loading…</span>
              : null}
            {data.timestamp
              ? <span style={{fontSize:'10px',color:'#adb5bd',marginLeft:'auto'}}>Updated: {data.timestamp}</span>
              : null}
          </div>

          {/* ── Error banner ── */}
          {error
            ? <div style={{padding:'10px 12px',color:'#e74c3c',fontSize:'12px',
                           background:'#fff5f5',borderBottom:'1px solid #ffd7d7'}}>
                Could not load Prometheus data: {errMsg}
              </div>
            : null}

          {/* ── Body — expands to full content height ── */}
          <div style={{padding:'10px 0'}}>

            {/* ── Host stat cards — all 11 in one row ── */}
            {data.nodeUptime
              ? <div style={{marginBottom:'14px',padding:'0 12px'}}>
                  {this.sectionLabel('Host System — ' + (data.nodeInstance || ''))}
                  <div style={{display:'grid',gridTemplateColumns:'repeat(11,1fr)',gap:'8px'}}>
                    {this.statCard('Uptime',      data.nodeUptime,                     'd:hh:mm', data.nodeUptimeClass)}
                    {this.statCard('CPU Cores',   data.nodeCpuCores,                   'cores',   '')}
                    {this.statCard('CPU Busy',    (data.nodeCpuBusy  || '0') + '%',    'current', data.nodeCpuBusyClass)}
                    {this.statCard('Load 5m',     (data.nodeSysLoad5m  || '0') + '%',  'avg',     data.nodeSysLoad5mClass)}
                    {this.statCard('Load 15m',    (data.nodeSysLoad15m || '0') + '%',  'avg',     data.nodeSysLoad15mClass)}
                    {this.statCard('RAM Total',   data.nodeRamTotal,                   'capacity','')}
                    {this.statCard('RAM Used',    (data.nodeRamUsed  || '0') + '%',    'used',    data.nodeRamUsedClass)}
                    {this.statCard('RootFS',      data.nodeRootFsTotal,                'capacity','')}
                    {this.statCard('RootFS Used', (data.nodeRootFsUsed || '0') + '%',  'used',    data.nodeRootFsUsedClass)}
                    {this.statCard('SWAP Total',  data.nodeSwapTotal,                  'capacity','')}
                    {this.statCard('SWAP Used',   (data.nodeSwapUsed || '0') + '%',    'used',    data.nodeSwapUsedClass)}
                  </div>
                </div>
              : null}

            {/* ── Instance Info ── */}
            {this.sectionLabel('Instance Info')}
            {this.svgPanel('Memory Usage %',      'libvirt_domain_memory_stats_used_percent',            data.memUsageChartSvg, 'memUsageChartSvg')}
            {this.svgPanel('CPU Utilization',      'rate(libvirt_domain_info_cpu_time_seconds_total[5m])',data.cpuChartSvg,      'cpuChartSvg')}
            {this.svgPanel('Storage Pool Usage %', '(capacity − available) / capacity',                  data.storageChartSvg,  'storageChartSvg')}
            {this.svgPanel('Memory Allocated',     'libvirt_domain_info_maximum_memory_bytes',            data.memAllocatedSvg,  'memAllocatedSvg')}
            {this.svgPanel('vCPU Count',           'libvirt_domain_info_virtual_cpus',                    data.vcpuSvg,          'vcpuSvg')}

            {/* ── Network Interface ── */}
            {this.sectionLabel('Network Interface')}
            {this.svgPanel('Network Traffic (TX + RX)',  'irate(interface_stats_transmit/receive_bytes_total[5m])',  data.netTrafficSvg,  'netTrafficSvg')}
            {this.svgPanel('Network Packets (TX + RX)', 'rate(interface_stats_receive/transmit_packets_total[5m])', data.netPacketsSvg,  'netPacketsSvg')}
            {this.svgPanel('Network Drops (TX + RX)',   'rate(interface_stats_transmit/receive_drops_total[5m])',   data.netDropsSvg,    'netDropsSvg')}
            {this.svgPanel('Network Errors (TX + RX)',  'rate(interface_stats_receive/transmit_errors_total[5m])',  data.netErrorsSvg,   'netErrorsSvg')}

            {/* ── Block / Volumes ── */}
            {this.sectionLabel('Block / Volumes')}
            {this.svgPanel('Write & Read Requests', 'irate(block_stats_write/read_requests_total[5m])', data.blockReqSvg,   'blockReqSvg')}
            {this.svgPanel('Write & Read Bytes',    'irate(block_stats_write/read_bytes_total[5m])',    data.blockBytesSvg, 'blockBytesSvg')}

            {/* ── Host System Charts ── */}
            {this.sectionLabel('Host System Charts')}
            {this.svgPanel('CPU — system / user / iowait / idle',  '', data.nodeCpuChartSvg,  'nodeCpuChartSvg')}
            {this.svgPanel('Memory — total / used / cache / free', '', data.nodeMemChartSvg,  'nodeMemChartSvg')}
            {this.svgPanel('Network ↑ RX / ↓ TX (bits/s)',        '', data.nodeNetChartSvg,  'nodeNetChartSvg')}
            {this.svgPanel('Disk Space Used %',                    '', data.nodeDiskChartSvg, 'nodeDiskChartSvg')}

          </div>

          <EmptyWidget isEmpty={loaded && !data.timestamp && !error} />
          <LoadingWidget isLoading={!loaded && !error} />

        </div>
      </Widget>
    );
  }
}

// Register with Morpheus component registry (dashboard grid uses this).
Morpheus.components.register('prometheus-dashboard-widget', PrometheusDashboardWidget);

// Also support direct mount for standalone page use.
$(document).ready(function () {
  var mount = document.querySelector('#prometheus-dashboard-widget');
  if (mount) {
    var root = ReactDOM.createRoot(mount);
    root.render(<PrometheusDashboardWidget />);
  }
});
