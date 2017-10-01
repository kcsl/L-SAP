---
layout: page
title: Discovered Bugs
permalink: /bugs/
---

Below is a list of all bug cases that have been discovered with L-SAP.

## Bug - 10/18/2015
**Version:**  `4.3-rc6`

**Source File:** `/v4.3-rc6/drivers/media/pci/ddbridge/ddbridge-core.c`

Function `tuner_attach_tda18271` (line 595) acquires a lock on `port->i2c_gate_lock` via the call to the function pointer (`input->fe->ops.i2c_gate_ctrl(input->fe, 1)`) (line 601) which calls function `drxk_gate_ctrl`. However, when the call to function `dvb_attach` on line 602 fails (returns `NULL`), the lock on `port->i2c_gate_lock` is never released.

The bug was currently on the master branch of `v4.3-rc6` as of writing the paper.

## Bug - Paper Case Study I            
**Version:** `3.19-rc1`

**Source File:** `/v3.19-rc1/drivers/mmc/host/toshsd.c`

Function `toshsd_thread_irq` has an unpaired lock `spin_lock_irqsave(&host->lock, flags)` at line 176. The bug occurs when the function returns at line 179 without unlocking the spin object host->lock.

The bug was spread across all the release candiates of `3.19-rc1` through `3.19-rc7` and got fixed in `4.0`. The fixing commit ID is: `8a66fdae771487762519db0546e9ccb648a2f911`.

## Bug - Paper Case Study II            
**Version:** `3.19-rc1`

**Source File:** `/v3.19-rc1/drivers/scsi/fnic/fnic_fcs.c`

Function `fnic_handle_fip_timer` has an unpaired lock `spin_lock_irqsave(&fnic->vlans_lock, flags)` at line 1284. The bug occurs when variable `vlan->state` has the value of `FIP_VLAN_AVAIL` when entering the switch block at line 1298. The switch block does not have a case to handle the value `FIP_VLAN_AVAIL`. Thus causing the function to return without unlocking the spin object `&fnic->vlans_lock`.

The bug has been reported and accepted, however it is still persisting on the latest Linux kernel branch.

## Bug - Paper Case Study III            
**Version:** `3.18-rc1`

**Source File:** `/v3.18-rc1/drivers/usb/dwc2/gadget.c`

Function `s3c_hsotg_ep_enable` has an unpaired lock `spin_lock_irqsave(&hsotg->lock, flags)` at line 2487. The bug occurs when the function returns at line 2565 without unlocking the spin object `&hsotg->lock`.

The bug was spread across `3.18-rc1` and `3.18-rc2` and fixed in `3.18-rc3`. The fixing commit ID is: `b585a48b8a9486f26a68886278f2c8f981676dd4`.

## Bug - Paper Case Study IV            
**Version:** `3.18-rc1`

**Source File:** `/v3.18-rc1/drivers/scsi/fnic/fnic_fcs.c`

Function `fnic_handle_fip_timer` has an unpaired lock `spin_lock_irqsave(&fnic->vlans_lock, flags)` at line 1279. The bug occurs when variable `vlan->state` has the value of `FIP_VLAN_AVAIL` when entering the switch block at line 1293. The switch block does not have a case to handle the value `FIP_VLAN_AVAIL`. Thus causing the function to return without unlocking the spin object `&fnic->vlans_lock`.

The bug has been reported and accepted, however it is still persisting on the latest Linux kernel branch.

## Bug - Paper Case Study V            
**Version:** `3.18-rc1`

**Source File:** `/v3.18-rc1/drivers/net/ethernet/stmicro/stmmac/stmmac_main.c`

Function `stmmac_xmit` has an unpaired lock `spin_lock(&priv->tx_lock)` at line 1906. The bug occurs when the function returns at line 2031 without unlocking the spin object `priv->tx_lock`.

The bug was spread across all the release candiates from `3.18-rc1` through `3.18-rc6` and got fixed in `3.18-rc7`. The fixing commit ID is: `758a0ab59b9bed75d8c8fcaed3cb41f10a586793`.

## Bug - Paper Case Study VI            
**Version:** `3.17-rc1`

**Source File:** `/v3.17-rc1/drivers/scsi/fnic/fnic_fcs.c`

Function `fnic_handle_fip_timer` has an unpaired lock `spin_lock_irqsave(&fnic->vlans_lock, flags)` at line 1278. The bug occurs when variable `vlan->state` has the value of `FIP_VLAN_AVAIL` when entering the switch block at line 1292. The switch block does not have a case to handle the value `FIP_VLAN_AVAIL`. Thus causing the function to return without unlocking the spin object `&fnic->vlans_lock`.

The bug has been reported and accepted, however it is still persisting on the latest Linux kernel branch.

## Bug - Paper Case Study VII            
**Version:** `3.17-rc1`

**Source File:** `/drivers/scsi/megaraid/megaraid_sas_fusion.c`

Function `megasas_reset_fusion` has an unpaired lock `mutex_lock(&instance->reset_mutex)` at line 2353. The bug occurs when the function returns at line 2358 without unlocking the mutex object `instance->reset_mutex`.

The bug has been fixed in `3.17-rc2`. The fixing commit ID is: `a2fbcbc3f0aa3bea3bf5c86e41f9c543c8de9e75`.

## Bug - Paper Case Study VIII            
**Version:** `3.13`

**Source File:** `/v3.13/fs/ext4/ioctl.c`

Function `swap_inode_boot_loader` has an unpaired lock that is acquired through the call to function: `lock_two_nondirectories(inode, inode_bl)` at line 133. The bug occurs when the function returns at line 147 without unlocking the mutex object `inode->mutex`. The locking/unlocking of the mutex object is performed inter-procedurally through the call to functions: `lock_two_nondirectories(inode, inode_bl)` for locking and `unlock_two_nondirectories(inode, inode_bl)` for unlocking.

The bug was spread across multiple major releases: `3.10`, `3.11`, `3.12`, and `3.13` and has been fixed in later release. The fixing commit ID is: `30d29b119ef01776e0a301444ab24defe8d8bef3`.

## Bug - Paper Case Study IX            
**Version:** `3.13`

**Source File:** `/drivers/gpu/drm/drm_crtc.c`

Function `drm_crtc_init` has an unpaired lock acquired through the inter-procedural call `drm_modeset_lock_all(dev)` at line 636. The bug occurs when the function returns at line 642 without unlocking the mutex object `config->mutex`. The locking/unlocking of the mutex object is performed inter-procedurally through the call to functions: `drm_modeset_lock_all(dev)` for locking and `drm_modeset_unlock_all(dev)` for unlocking.

The bug has been accepted and became obselete in next release 3.14-rc1.

## Bug - Paper Case Study X            
**Version:** `3.12`

**Source File:** `/v3.12/net/core/netpoll.c`

Function `netpoll_send_skb_on_dev` has an unpaired lock acquired through the true branch of the branch condition for the inter-procedural call `__netif_tx_trylock(txq)` at line 383. The bug occurs when the function breaks the loop at line 390 without unlocking the spin object `txq->_xmit_lock`. The locking/unlocking of the mutex object is performed inter-procedurally through the call to functions: `__netif_tx_trylock` which locks an object if the call returns non-zero and `__netif_tx_unlock` for unlocking.

The bug was fixed in later release. The fixing commit ID is: `aca5f58f9ba803ec8c2e6bcf890db17589e8dfcc`.

## Bug - Paper Case Study XI            
**Version:** `3.3`

**Source File:** `/v3.3/net/bluetooth/rfcomm/tty.c`

Function `rfcomm_tty_open` has an unpaired lock acquired through the inter-procedural call to `tty_lock` at line 728. The bug occurs when the function breaks the infinite loop at lines 715, 719, and 723 without unlocking the mutex object `tty->legacy_mutex`. The locking/unlocking of the mutex object is performed inter-procedurally through the call to functions: `tty_lock` for locking and tty_unlock for unlocking.

The bug became obselete at next release.

## Bug - Paper Case Study XII            
**Version:** `3.2`

**Source File:** `/v3.2/drivers/net/wireless/wl12xx/sdio.c`

In function `wl1271_remove` the mutex object `wl->mutex` may get locked upon the return of function `wl1271_unregister_hw` through the call to function `__wl1271_plt_stop`. However, the lock is never released upon exit of function `wl1271_unregister_hw`. The buggy scenario happens as follows: `wl1271_remove` calls `wl1271_unregister_hw`, which acquires the locks. Then, `wl1271_remove` calls `wl1271_free_hw`, which tries to lock the object the already locked in `wl1271_unregister_hw`, which causes a deadlock scenario.

The bug persisted over `3.2` and `3.3` releases and got fixed in fixed in later release. The fixing commit ID is: `f3df1331f25f782e838a3ecb72cec86b539ac02f`.

