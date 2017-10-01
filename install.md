---
layout: page
title: Install
permalink: /install/
---

Installing L-SAP is easy. L-SAP is distributed as an Eclipse plugin. It is recommended to install the plugin from the provided update site, but it is also possible to install from source.

Note that L-SAP requires the [Atlas](http://www.ensoftcorp.com/atlas/) program analysis platform which is available free for [academic use](http://www.ensoftcorp.com/atlas/academic-license/) from EnSoft Corp. When you fill out the license request form, please mention L-SAP.
        
### Installing from Update Site (recommended)
1. Start Eclipse, then select `Help` &gt; `Install New Software`.
2. Click `Add`, in the top-right corner.
3. In the `Add Repository` dialog that appears, enter &quot;Atlas Toolboxes&quot; for the `Name` and &quot;[https://ensoftcorp.github.io/toolbox-repository/](https://ensoftcorp.github.io/toolbox-repository/)&quot; for the `Location`.
4. In the `Available Software` dialog, select the checkbox next to "L-SAP" and click `Next` followed by `OK`.
5. In the next window, you'll see a list of the tools to be downloaded. Click `Next`.
6. Read and accept the license agreements, then click `Finish`. If you get a security warning saying that the authenticity or validity of the software can't be established, click `OK`.
7. When the installation completes, restart Eclipse.

## Installing from Source
If you want to install from source for bleeding edge changes, first grab a copy of the [source](https://github.com/ kcsl/L-SAP) repository. In the Eclipse workspace, import the `com.kcsl.lsap` Eclipse project located in the source repository.  Right click on the project and select `Export`.  Select `Plug-in Development` &gt; `Deployable plug-ins and fragments`.  Select the `Install into host. Repository:` radio box and click `Finish`.  Press `OK` for the notice about unsigned software.  Once Eclipse restarts the plugin will be installed and it is advisable to close or remove the `com.kcsl.lsap` project from the workspace.

## Changelog
Note that except for it's original release, L-SAP version numbers are based off [Atlas](http://www.ensoftcorp.com/atlas/download/) version numbers.

### 3.1.7
- Ported code to Atlas 3.1.7

### 0.1
- Original release, preserved here for posterity.
- Download: [L-SAP-0.1.tar.gz](/L-SAP/updates/L-SAP-0.1.tar.gz)
