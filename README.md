# HPE Morpheus Community Plugins

> **Community Plugins — Not Officially Supported by HPE**
>
> These plugins are built by HPE as community contributions. They are **not** official HPE products and carry no HPE support, SLA or warranty. You are free to use them, fork them, and extend them to suit your environment. Pull requests and adaptations are welcome.

---

## About This Repository

This repository is the central home for all community-built Morpheus plugins developed by HPE. Each plugin lives in its own subdirectory and targets one or more HPE Morpheus editions (VM Essentials, Enterprise, or Advanced) and use-cases.

Plugins here are developed to fill gaps not covered by native Morpheus functionality or official HPE integrations — things like custom dashboards, third-party monitoring integrations, approval workflows, and more.

---

## Plugins

| Plugin                                                       | Description                                                                                             | Tested Versions |
| ------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------- | --------------- |
| [prometheus-dashboard-plugin](./prometheus-dashboard-plugin) | Renders Ubuntu OS & libvirt metrics for HVM hosts & VMs directly on the Morpheus Manager home dashboard | 8.1.1, 9.0.0    |

---

## Getting Started

Each plugin has its own `README.md` and `INSTALL.md` inside its subdirectory with build and installation instructions. The general process is:

1. Navigate into the plugin folder.
2. Download the .jar file
3. Upload the resulting jar via **Administration → Integrations → Plugins** in your Morpheus Manager.

---

## Disclaimer

This project is open-source software provided **as-is**, under the terms of the MIT License.

- **No Warranty:** The project comes with no guarantees regarding stability, security, or performance. Use it at your own risk.
- **No Official Support:** This is a community project. The maintainers have no obligation to fix bugs, provide custom assistance, or answer support queries.

For full legal terms regarding liability and warranty, please see the [LICENSE](LICENSE) file.
