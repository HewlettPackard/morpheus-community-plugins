/**
 * Prometheus Dashboard — dashboard-level script placeholder.
 *
 * Required because Morpheus' dashboard sync calls scriptPathForPlugin() on the
 * Dashboard.scriptPath and NPEs if null. The actual widget rendering is handled
 * by the item-type's compiled React widget (prometheus-dashboard-widget.js),
 * so this file is intentionally a no-op.
 */
(function () {
  // intentionally empty — widget mounts via its own scriptPath
})();
