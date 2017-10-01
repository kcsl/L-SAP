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

## Downloading the Source Code of Linux Kernel
You can download a specific Linux kernel versions from [here](https://www.kernel.org/pub/linux/kernel/v4.x/testing/). Or you can download any of the kernel versions used in the L-SAP paper: [3.17-rc1](https://www.kernel.org/pub/linux/kernel/v3.x/testing/linux-3.17-rc1.tar.gz), [3.18-rc1](https://www.kernel.org/pub/linux/kernel/v3.x/testing/linux-3.18-rc1.tar.gz), or [3.19-rc1](https://www.kernel.org/pub/linux/kernel/v3.x/testing/linux-3.19-rc1.tar.gz).

Once downloaded, extract the downloaded linux-<version>.tar.gz:

    tar -zxvf linux-<version>.tar.gz ~/linux-workspace/

Then, go to the directory `/linux-workspace/linux-<version>/`

    cd ~/linux-workspace/linux-<version>/

Then, configure the Linux kernel with your own taste of configurations. To configure the Linux kernel with the same configurations as in the L-SAP paper, run the following command:

    make allmodconfig

### Analysis Configuration
1. Because indexing the Linux kernel with Atlas requires a lot of space, we recommend that you change the parameter `-Xms` and `-Xms` in eclipse/eclipse.ini to bigger values according to your system. Note that Atlas provides a handy interface under the `Atlas` &gt; `Manage Project Settings` &gt; `Advanced` menu.

2. Run Eclipse and select `~/linux-workspace/` directory as the current Eclipse workspace.

3. Once Eclipse opens, go to `File` &gt; `Import` &gt; `Existing Code as Makefile Project`.

4. Next, browse to directory `~/linux-workspace/linux-<version>/`and choose `Linux GCC` as the Toolchain for Indexer Setting, then press `Finish`.

5. Right click on the imported Linux Kernel project and select `Properties`.

6. From `Atlas C/C++ Build` tab, click the `Enable Atlas Error Parser` button. Check (enable) the checkboxes corresponding to `Existential Indexer` and `Dataflow Indexer`.

7. From `C/C++ Build` tab, set the `Build command` to `make V=1`, From the `Behavior` tab, set the `Build (Incremental build)` field to the folder of interest to build (for example, `drivers/`). Finally, set the `Clean` command to `mrproper`.

8. From `C/C++ Buid/Settings/Error Parsers` tab, enable the `Atlas Error Parser`.

9. Build the Linux kernel by right clicking on the Linux kernel project from Eclipse and selecting `Build Project`. This process will take around 10 minutes if you have a powerful machine.

10. Next map the Linux kernel in Atlas by selecting the Linux kernel project under `Atlas` > `Manage Project Settings`, moving the project to the `Map` column, and pressing `Save &amp; Re-map`. Mapping the workspace will take some time (approximately 2-3 hours based on your machine).

11. You are now ready to run L-SAP on the Linux kernel. Please, refer to the [Tutorials](/L-SAP/tutorials) page for more info on how to run L-SAP pairing analysis.

## Changelog
Note that except for it's original release, L-SAP version numbers are based off [Atlas](http://www.ensoftcorp.com/atlas/download/) version numbers.

### 3.1.7
- Ported code to Atlas 3.1.7

### 0.1
- Original release, preserved here for posterity.
- Download: [L-SAP-0.1.tar.gz](/L-SAP/updates/L-SAP-0.1.tar.gz)
