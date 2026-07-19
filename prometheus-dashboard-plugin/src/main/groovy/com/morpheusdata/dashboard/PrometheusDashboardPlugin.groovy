// SPDX-FileCopyrightText:  Copyright Hewlett Packard Enterprise Development LP

package com.morpheusdata.dashboard

import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.views.HandlebarsRenderer
import groovy.util.logging.Slf4j

/**
 * Prometheus Dashboard Plugin.
 *
 * Registers a full Morpheus native dashboard widget powered by Prometheus.
 * Data is fetched from the PrometheusDashboardController (JSON API) and
 * rendered in a React widget on the Morpheus home dashboard grid.
 *
 * All SVG charts are pre-built server-side by PrometheusQueryHelper and
 * injected into the React widget via dangerouslySetInnerHTML, keeping the
 * browser side simple and avoiding any direct Prometheus connection.
 */
@Slf4j
class PrometheusDashboardPlugin extends Plugin {

    // Init block — set renderer before registerPlugin to avoid
    // DynamicTemplateLoader crash on HPE Morpheus 8.1.x.
    {
        this.renderer = new HandlebarsRenderer()
    }

    @Override
    String getCode() { 'prometheusDashboard' }

    @Override
    String getName() { 'Prometheus Dashboard' }

    @Override
    void initialize() {
        setName('Prometheus Dashboard')
        setDescription('Full libvirt + Node Exporter Prometheus metrics as a native Morpheus home dashboard widget. Includes CPU, memory, storage, network, block I/O, and host system charts with 1h/6h/12h/1d time range picker.')

        // Controller — serves JSON API endpoints the React widget polls.
        PrometheusDashboardController controller = new PrometheusDashboardController(this, morpheus)
        this.controllers.add(controller)
        this.registerProvider(controller)

        // Dashboard widget item type (the React widget itself).
        PrometheusDashboardItemProvider itemProvider = new PrometheusDashboardItemProvider(this, morpheus)
        this.registerProvider(itemProvider)

        // The Morpheus dashboard that hosts the widget.
        PrometheusDashboardProvider dashboardProvider = new PrometheusDashboardProvider(this, morpheus)
        this.registerProvider(dashboardProvider)

        log.info("Prometheus Dashboard plugin initialized")
    }

    @Override
    void onDestroy() {
        log.info("Prometheus Dashboard plugin destroyed")
    }

    // ── Plugin settings (Administration → Plugins → Prometheus Dashboard) ────
    // Values are persisted in the Morpheus DB and read at query time.
    // Change these in the UI — no rebuild required.
    @Override
    List<OptionType> getSettings() {
        [
            new OptionType(
                name: 'Prometheus Host', code: 'promDash.promHost',
                fieldName: 'promHost', fieldLabel: 'Prometheus Host',
                inputType: OptionType.InputType.TEXT, required: true,
                defaultValue: '', displayOrder: 0
            ),
            new OptionType(
                name: 'Prometheus Port', code: 'promDash.promPort',
                fieldName: 'promPort', fieldLabel: 'Prometheus Port',
                inputType: OptionType.InputType.TEXT, required: true,
                defaultValue: '9090', displayOrder: 1
            ),
            new OptionType(
                name: 'Prometheus Username', code: 'promDash.promUser',
                fieldName: 'promUser', fieldLabel: 'Prometheus Username',
                inputType: OptionType.InputType.TEXT, required: false,
                defaultValue: '', displayOrder: 2
            ),
            new OptionType(
                name: 'Prometheus Password', code: 'promDash.promPass',
                fieldName: 'promPass', fieldLabel: 'Prometheus Password',
                inputType: OptionType.InputType.PASSWORD, required: false,
                displayOrder: 3
            ),
            new OptionType(
                name: 'Node Exporter Instance', code: 'promDash.nodeInstance',
                fieldName: 'nodeInstance', fieldLabel: 'Node Exporter Instance (host:port)',
                inputType: OptionType.InputType.TEXT, required: false,
                defaultValue: '', displayOrder: 4
            ),
            new OptionType(
                name: 'Node Exporter Job', code: 'promDash.nodeJob',
                fieldName: 'nodeJob', fieldLabel: 'Node Exporter Job Name',
                inputType: OptionType.InputType.TEXT, required: false,
                defaultValue: 'hvm-os', displayOrder: 5
            ),
        ]
    }

    Boolean hasCustomRenderer() { return true }
}
