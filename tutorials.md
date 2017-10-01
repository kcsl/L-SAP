---
layout: page
title: Tutorials
permalink: /tutorials/
---

Once Atlas has mapped the Linux kernel, you can now run the analysis scripts. If you have not completed this step, please refer to the configuration section of the [Install](/L-SAP/install) page.

First open the Atlas Shell to invoke L-SAP. To open the Atlas Shell from the Eclipse menu toolbar, navigate to `Atlas` &gt; `Open Atlas Shell`. 

Next run the L-SAP analysis. All the analysis logic reside in the Java class `LinuxScripts`. Before invoking the analysis scripts, you need to set the following flags/strings in code to your preferences:

- `SHOW_GRAPHS` field: if it is set to true, the script will produce the CFGs, MPGs, and EFGs for each signature in the lock/unlock pairing analysis.

- `WORKSPACE_PATH` field: to determine the path where the analysis results will be written.

If you want to invoke the spin/mutex lock/unlock pairing analysis, then write into the Atlas Shell the respective commands:

    var analysis = new LinuxScripts()
    analysis.verifySpin(true) 

For spin lock/unlock pairing analysis OR `analysis.verifyMutex(true)` for mutex lock/unlock pairing analysis. The `true`/`false` argument passed to each function correspond to whether to enable feasibility checking for the potentially-error paths if found.

The analysis results will written into `WORKSPACE_PATH` folder.