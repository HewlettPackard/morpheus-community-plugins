# Morpheus Community Plugin — Process & Reference Guide

> This document tracks every step required to build, approve, and ship a Morpheus community plugin at HPE, along with all reference links used along the way.

---

## Table of Contents

1. [Plugin Development Steps](#1-plugin-development-steps)
2. [Approval Process](#2-approval-process)
   - [OSRB (Open Source Review Board)](#21-osrb-open-source-review-board)
   - [VTN (Vulnerability Tracking & Notification)](#22-vtn-vulnerability-tracking--notification)
   - [STROSS Scan](#23-stross-scan)
   - [C360 Approval](#24-c360-approval)
3. [Reference Links](#3-reference-links)

---

## 1. Plugin Development Steps

### 1.0 Recommended Approach: Adapt an Existing Plugin

> **Do not start from a blank project.** Building a Morpheus plugin entirely from scratch is time-consuming and error-prone. The strongly recommended approach is to clone an existing plugin that is closest to what you need and modify it.

**Why this matters:**

- The Morpheus plugin ecosystem has well-maintained sample plugins for nearly every provider type. These already have the correct `build.gradle` setup, manifest attributes, folder structure, and working provider skeletons.
- Subtle wiring issues (classloader isolation, shadowJar configuration, manifest attributes, provider registration) are already solved in the samples.
- You avoid spending time on boilerplate and can focus on the actual business logic.

**How to choose a starting point:**

1. Go to the [Plugin Samples Repository](https://github.com/gomorpheus/morpheus-plugin-samples) and identify the sample that most closely matches your provider type.
2. Clone it, rename the package and class names, and strip out the logic you don't need.
3. Incrementally add your own logic on top.

---

### 1.0.1 HPE Morpheus Edition Compatibility

Before picking a base plugin and starting development, confirm which HPE Morpheus edition(s) the plugin needs to support:

| Edition                        | Key Characteristics                                                                                                |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------ |
| **HPE Morphues VM Essentials** | Core VM lifecycle management on HVM/KVM; limited cloud integrations; no advanced automation workflows              |
| **HPE Morphues VM Enterprise** | Adds multi-cloud support, IPAM/DNS integrations, approval workflows, backup integrations, custom reports           |
| **HPE Morphues VM Advanced**   | Full feature set — all of the above plus analytics, guidance, advanced cost management, and full API extensibility |

**What to check before you start:**

- The provider type you are implementing must be supported on the target edition. For example, `ApprovalProvider` and `BackupProvider` require Enterprise or above; `DatastoreTypeProvider` for HVM is available on VM Essentials and above.
- The minimum appliance version you set in `Morpheus-Min-Appliance-Version` in the jar manifest must match the version running in the target environment.
- If you are extending an existing sample plugin, verify that it does not depend on features or APIs exclusive to a higher edition than your target.
- Test the built jar against the specific edition before submitting for approval.

---

### 1.1 Prerequisites

- Java 11 (OpenJDK recommended; set `sourceCompatibility = '1.11'` in Gradle)
- Gradle 7.x recommended (`gradle wrapper` to generate the wrapper if missing)
- IDE: IntelliJ IDEA (auto-generates required interface method stubs)
- Groovy 3.0.x (included with Morpheus runtime — plugins can be written in Java or Groovy)

---

### 1.2 Project Setup

1. Create a new empty project folder.
2. Create `build.gradle` using the `shadowJar` (fat-jar) pattern:

   ```groovy
   plugins {
       id "com.bertramlabs.asset-pipeline" version "4.3.0"
       id "com.github.johnrengelman.shadow" version "6.0.0"
   }

   apply plugin: 'com.morpheusdata.morpheus-plugin-gradle'
   apply plugin: 'java'
   apply plugin: 'groovy'

   group = 'com.hpe.example'
   version = '1.0.0'
   sourceCompatibility = '1.11'
   targetCompatibility = '1.11'

   repositories { mavenCentral() }

   configurations { provided }

   dependencies {
       provided 'com.morpheusdata:morpheus-plugin-api:1.3.0'
       provided 'org.codehaus.groovy:groovy-all:3.0.9'
   }

   tasks.assemble.dependsOn tasks.shadowJar
   ```

3. Set the jar manifest so Morpheus knows which class to load:

   ```groovy
   jar {
       manifest {
           attributes(
               'Plugin-Class':   'com.hpe.example.MyPlugin',
               'Plugin-Version': archiveVersion.get(),
               'Morpheus-Name':  'My Plugin Name',
               'Morpheus-Organization': 'HPE',
               'Morpheus-Code':  'my-plugin-code',
               'Morpheus-Description': 'Short description',
               'Morpheus-Logo':  'assets/myplugin.svg',
               'Morpheus-Repo':  'https://github.com/HewlettPackard/morpheus-community-plugins',
               'Morpheus-Min-Appliance-Version': '8.0.0'
           )
       }
   }
   ```

4. Create the standard folder structure:

   ```
   src/main/groovy/          ← plugin source code
   src/main/resources/renderer/hbs/  ← Handlebars view templates
   src/main/resources/i18n/  ← localization properties
   src/main/resources/scribe/← data seeding HCL files
   src/assets/images/
   src/assets/javascript/
   src/assets/stylesheets/
   src/test/groovy/
   ```

5. Add `build/` to `.gitignore`.

---

### 1.3 Create the Plugin Entry Class

```groovy
import com.morpheus.core.Plugin

class MyPlugin extends Plugin {
    @Override
    void initialize() {
        this.setName('My Plugin')
        // Register all providers here
        this.registerProvider(new MyCustomProvider(this, morpheus))
    }
}
```

---

### 1.4 Implement Providers

Choose the provider type(s) that match the plugin's purpose:

| Provider Type                  | Use Case                               |
| ------------------------------ | -------------------------------------- |
| `InstanceTabProvider`          | Custom tab on the Instance detail page |
| `ServerTabProvider`            | Custom tab on the Server detail page   |
| `TaskProvider`                 | Custom automation task type            |
| `ApprovalProvider`             | Custom ITSM approval integration       |
| `IPAMProvider` / `DNSProvider` | IP address management / DNS            |
| `CloudProvider`                | Custom cloud type                      |
| `BackupProvider`               | Custom backup integration              |
| `ReportProvider`               | Custom report type                     |
| `DatastoreTypeProvider`        | Custom storage datastore for HVM/KVM   |
| `GenericIntegrationProvider`   | General-purpose integration            |
| `NetworkProvider`              | Custom network provider                |

Register every provider in `initialize()`:

```groovy
this.registerProvider(new MyProvider(this, morpheus))
```

---

### 1.5 Build the Plugin

```bash
./gradlew shadowJar
```

The resulting jar is in `build/libs/`. Upload it via **Administration → Integrations → Plugins** in the Morpheus UI.

---

### 1.6 Seed Data (Optional)

If the plugin needs to pre-populate Morpheus objects (layouts, option types, plans, etc.), create `.scribe` HCL files in `src/main/resources/scribe/`. These are processed on plugin install or reload.

```hcl
resource "option-type" "my-option" {
  name        = "My Option"
  code        = "my-option"
  fieldName   = "myOption"
  fieldContext = "config"
  fieldLabel  = "My Option"
  type        = "text"
  displayOrder = 0
  required    = true
}
```

---

### 1.7 Testing

Use [Spock Framework](http://spockframework.org/) to unit-test providers by mocking `MorpheusContext`:

```groovy
class MyProviderSpec extends Specification {
    @Shared MorpheusContext context = Mock(MorpheusContextImpl)
    @Subject MyProvider provider

    void setup() {
        provider = new MyProvider(Mock(MyPlugin), context)
    }

    void "test my method"() {
        when:
        def result = provider.myMethod()
        then:
        result.success == true
    }
}
```

---

### 1.8 Versioning & Publishing

1. Update `version` in `build.gradle`.
2. Rebuild with `./gradlew shadowJar`.
3. Upload the new jar to Morpheus, or publish to the community plugin repository.
4. Tag the release in Git.

---

## 2. Approval Process

The following approvals are required before the plugin can be published or distributed within HPE.

---

### 2.1 OSRB (Open Source Review Board)

The OSRB review ensures that all open-source dependencies and any open-source code contributions comply with HPE's open-source policy.

**Steps:**

1. Identify all third-party open-source libraries included in the plugin (listed in `build.gradle` under `implementation` / `compileOnly`).
2. Submit an OSRB request through the internal HPE OSRB portal.
3. Provide the license type, version, and intended use for each dependency.
4. Await OSRB approval before publishing any code externally.
5. Record the OSRB ticket number here once obtained: `OSRB-XXXXXXX`

**Key things OSRB checks:**

- License compatibility (GPL, LGPL, Apache, MIT, etc.)
- Whether any code is being contributed back to an open-source project
- Export control classification

---

### 2.2 VTN (Vulnerability Tracking & Notification)

VTN tracks known vulnerabilities (CVEs) in the dependencies and codebase.

**Steps:**

1. Run a dependency vulnerability scan (e.g., OWASP Dependency-Check or Snyk) against all jars bundled in the shadowJar.
2. Submit findings to the VTN system.
3. Remediate any Critical or High CVEs before requesting sign-off.
4. Obtain VTN clearance and record the ticket: `VTN-XXXXXXX`

**Tips:**

- Add the OWASP Dependency-Check Gradle plugin to your build for automated scanning.
- Suppress false positives using a suppression XML file and document justifications.

---

### 2.3 STROSS Scan

STROSS (Security Threat and Risk Operations Security Scanning) is HPE's static analysis and security scanning process.

**Steps:**

1. Submit the source code repository to the STROSS scanning pipeline.
2. Address all Critical and High findings from the scan report.
3. For any accepted risks (Medium/Low that cannot be immediately remediated), document justifications in the STROSS portal.
4. Obtain STROSS sign-off and record the scan ID: `STROSS-XXXXXXX`

**Common checks STROSS performs:**

- Static application security testing (SAST)
- Secrets / credential leakage detection
- Insecure coding patterns (OWASP Top 10)
- Dependency license and vulnerability analysis

---

### 2.4 C360 Approval

C360 is HPE's compliance and controls sign-off process, ensuring the deliverable meets product security and compliance standards before release.

**Steps:**

1. Complete all prior approvals (OSRB, VTN, STROSS) first — C360 typically requires evidence of these.
2. Open a C360 request in the HPE compliance portal, attaching:
   - OSRB approval ticket
   - VTN clearance ticket
   - STROSS scan report and sign-off
   - A description of the plugin, its data flows, and any external integrations
3. Work through any C360 questionnaire items with your security and compliance contacts.
4. Record the C360 approval reference: `C360-XXXXXXX`

---

## 3. Reference Links

### Morpheus Plugin Development

| Description                   | Link                                                  |
| ----------------------------- | ----------------------------------------------------- |
| Morpheus Plugin Documentation | https://developer.morpheusdata.com/docs               |
| Plugin Examples               | https://developer.morpheusdata.com/docs#_examples-2   |
| Plugin Samples Repository     | https://github.com/gomorpheus/morpheus-plugin-samples |
| Morpheus Plugin Core GitHub   | https://github.com/gomorpheus/morpheus-plugin-core    |
| Full API Javadoc              | https://developer.morpheusdata.com/api/               |

### Build & Runtime

| Description                 | Link                     |
| --------------------------- | ------------------------ |
| Groovy Language             | https://groovy-lang.org/ |
| Gradle Build Tool           | https://gradle.org/      |
| ReactiveX / RxJava concepts | http://reactivex.io/     |

### HPE Internal

| Description                                   | Link                                                                                            |
| --------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| OSRB Process (Downstream / Community)         | https://hpe.sharepoint.com/sites/OSPO/SitePages/osrp_process_downstream.aspx                    |
| OSRB Jira Project (OSPO220224)                | https://jira-pro.it.hpe.com:8443/projects/OSPO220224/issues/OSPO220224-360?filter=allopenissues |
| VTN+ (Vulnerability Tracking & Notification)  | https://vtn.hpecorp.net/#/login                                                                 |
| STROSS Scan Portal                            | https://stross.in.rdlabs.hpecorp.net/                                                           |
| HPE027-09 Corporate Standard (C360 reference) | https://hpe.sharepoint.com/teams/CorporateStdDMT/Approved/HPE027-09.pdf                         |
| HPE GitHub Organization                       | https://github.com/HewlettPackard                                                               |
