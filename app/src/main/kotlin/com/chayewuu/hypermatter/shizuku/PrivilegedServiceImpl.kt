package com.chayewuu.hypermatter.shizuku

import android.os.IBinder
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs inside the Shizuku / Stellar (adb/shell-uid) server process. Toggles
 * the OEM-deny firewall chain + per-uid rule for a package — used to
 * temporarily cut com.xiaomi.xmsf (小米云服务) off the network so its
 * cloud-side 超级岛 whitelist check fails and HyperOS falls back to
 * rendering the island locally.
 *
 * 入口必须走 **connectivity**，不能用 network_management（这是与
 * NexioSchedule 实测可用实现一致的关键点）：
 *   - INetworkManagementService.setFirewallChainEnabled 会校验
 *     「Only available to AID_SYSTEM」，shell uid(2000) 不是 AID_SYSTEM，
 *     在 HyperOS 4 / Android 17 上直接 SecurityException。
 *   - IConnectivityManager.setFirewallChainEnabled 在 shell 进程里没有
 *     hiddenapi 限制，ServiceManager 拿到原始 binder 后 asInterface
 *     得到的代理可以直接 transact 过去，实测可用。
 * 因此这里只取 connectivity，不再尝试 network_management。
 *
 * 该类由 Shizuku/Stellar 在自己的进程里按类名反射实例化，**必须**在
 * proguard-rules.pro 里 keep 住无参构造，否则 R8 会把它优化掉，绑定时报
 * NoSuchMethodException: <obfuscated>.<init> []。
 */
class PrivilegedServiceImpl : IPrivilegedService.Stub() {
    companion object {
        private const val TAG = "PrivilegedServiceImpl"

        /** FirewallChain.OEM_DENY —— 厂商自定义拒绝链，adb 权限可下发。 */
        private const val FIREWALL_CHAIN_OEM_DENY = 9

        /** FirewallRule 动作：0 = ALLOW，2 = DENY。 */
        private const val RULE_ALLOW = 0
        private const val RULE_DENY = 2

        private const val TIMEOUT_SECONDS = 3L
    }

    override fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean): Boolean {
        val action = if (enabled) RULE_ALLOW else RULE_DENY
        Log.i(TAG, "setPackageNetworkingEnabled uid=$uid enabled=$enabled rule=$action")

        val iConnectivityManager = try {
            loadConnectivityManager()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot obtain IConnectivityManager", e)
            return false
        } ?: return false

        val latch = CountDownLatch(1)
        var result = false

        val thread = Thread({
            try {
                // 先开链再下发规则，顺序不能反：链关闭时下发规则会被丢弃。
                val setChainEnabled = iConnectivityManager.javaClass.getMethod(
                    "setFirewallChainEnabled",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                )
                setChainEnabled.invoke(iConnectivityManager, FIREWALL_CHAIN_OEM_DENY, true)

                val setUidRule = iConnectivityManager.javaClass.getMethod(
                    "setUidFirewallRule",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                setUidRule.invoke(iConnectivityManager, FIREWALL_CHAIN_OEM_DENY, uid, action)

                result = true
                Log.i(TAG, "Firewall updated: chain=$FIREWALL_CHAIN_OEM_DENY uid=$uid rule=$action")
            } catch (e: Exception) {
                Log.e(TAG, "Firewall call failed for uid=$uid", e)
                result = false
            } finally {
                latch.countDown()
            }
        }, "firewall").apply { isDaemon = true }

        thread.start()
        val completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!completed) {
            thread.interrupt()
            Log.w(TAG, "Firewall operation timed out")
            return false
        }
        return result
    }

    /**
     * 取 connectivity 服务的 IConnectivityManager 代理。
     *
     * 必须用 [android.os.ServiceManager] 拿**原始 binder** 再 asInterface，
     * 不能用 ConnectivityManager 的客户端代理（那样反射拿不到平台方法）。
     */
    private fun loadConnectivityManager(): Any? {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, "connectivity") as? IBinder
            ?: throw IllegalStateException("Connectivity service not found")

        return Class.forName("android.net.IConnectivityManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
    }
}
