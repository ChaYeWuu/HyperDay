package com.chayewuu.hypermatter.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Shizuku 接入（移植自 NexioSchedule）：
 * - Sui.init + Shizuku.pingBinder 探测可用性
 * - requestPermission 拉起 Shizuku 授权
 * - setXmsfNetworkingEnabled: 优先 UserService（IPrivilegedService），
 *   失败降级为直连 binder 反射（IConnectivityManager.setFirewallChainEnabled
 *   + setUidFirewallRule，OEM deny 链 + uid 规则）
 *
 * UserService 绑定是这条链路的命门：PrivilegedServiceImpl 必须在
 * AndroidManifest 里声明成 exported <service>，否则 Shizuku 的
 * bindUserService 永远绑不上，bypass 会静默失败并降级成普通通知。
 */
object ShizukuManager {
    private const val TAG = "ShizukuManager"
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    private const val FIREWALL_CHAIN_OEM_DENY = 9

    /** UserService 首次绑定等待时长；Shizuku 冷启动时可能较慢。 */
    private const val BIND_TIMEOUT_MS = 2000L

    /** 绑定失败后的重试次数（Shizuku 冷启动竞态）。 */
    private const val BIND_RETRIES = 2

    private var initialized = false
    private var privilegedService: IPrivilegedService? = null

    @Volatile
    private var serviceConnected = false

    private val bindLock = Any()
    private var bindLatch = CountDownLatch(1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                privilegedService = IPrivilegedService.Stub.asInterface(binder)
                serviceConnected = true
                bindLatch.countDown()
                Log.i(TAG, "Privileged service connected")
            } else {
                Log.w(TAG, "onServiceConnected with dead binder")
                bindLatch.countDown()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            privilegedService = null
            serviceConnected = false
            synchronized(bindLock) { bindLatch = CountDownLatch(1) }
            Log.w(TAG, "Privileged service disconnected")
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName("com.chayewuu.hypermatter", PrivilegedServiceImpl::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("privileged")
            .debuggable(false)
            .version(1)
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                // Shizuku 重启后清掉失效的本地引用，下次调用重新绑定。
                Shizuku.addBinderReceivedListenerSticky {
                    Log.i(TAG, "Shizuku binder received, service state reset")
                    privilegedService = null
                    serviceConnected = false
                    synchronized(bindLock) { bindLatch = CountDownLatch(1) }
                }
                initialized = true
                Log.i(TAG, "Shizuku initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Shizuku", e)
            }
        }
    }

    fun isShizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) {
        false
    }

    fun checkSelfPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    /** Shizuku 已运行且本应用已获得授权。 */
    fun isAuthorized(context: Context): Boolean {
        init(context)
        return isShizukuRunning() && checkSelfPermission()
    }

    fun requestPermission(context: Context, callback: (Boolean) -> Unit) {
        init(context)
        if (!isShizukuRunning()) {
            callback(false)
            return
        }
        if (checkSelfPermission()) {
            callback(true)
            return
        }

        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                callback(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }

        Shizuku.addRequestPermissionResultListener(listener)
        try {
            Shizuku.requestPermission(1001)
        } catch (e: Exception) {
            Shizuku.removeRequestPermissionResultListener(listener)
            Log.e(TAG, "requestPermission failed", e)
            callback(false)
        }
    }

    /**
     * 切断/恢复 XMSF（小米云服务）网络。切断后 XMSF 云端超级岛白名单
     * 校验失败，系统走本地兜底渲染超级岛；发完通知必须恢复。
     *
     * @return true 表示规则已实际下发（调用方可以放心 post 通知）。
     */
    fun setXmsfNetworkingEnabled(context: Context, enabled: Boolean): Boolean {
        init(context)
        if (!isShizukuRunning() || !checkSelfPermission()) {
            Log.w(TAG, "Shizuku not available or no permission")
            return false
        }

        val xmsfUid = try {
            context.packageManager.getPackageUid(XMSF_PACKAGE, 0)
        } catch (e: Exception) {
            Log.e(TAG, "XMSF package $XMSF_PACKAGE not found", e)
            return false
        }
        Log.i(TAG, "setXmsfNetworkingEnabled uid=$xmsfUid enabled=$enabled")

        // 1) UserService（adb 权限，最可靠）。
        return try {
            val viaService = setPackageNetworkingEnabledViaService(xmsfUid, enabled)
            if (viaService) {
                Log.i(TAG, "XMSF networking via UserService: $enabled")
                true
            } else {
                Log.w(TAG, "UserService route returned false, trying binder fallback")
                setPackageNetworkingEnabledViaBinder(xmsfUid, enabled)
            }
        } catch (e: Exception) {
            Log.e(TAG, "UserService route threw, trying binder fallback", e)
            try {
                setPackageNetworkingEnabledViaBinder(xmsfUid, enabled)
            } catch (e2: Exception) {
                Log.e(TAG, "Binder fallback also failed", e2)
                false
            }
        }
    }

    private fun setPackageNetworkingEnabledViaService(uid: Int, enabled: Boolean): Boolean {
        val service = getPrivilegedService()
        if (service == null) {
            Log.w(TAG, "UserService unavailable (bind failed)")
            return false
        }
        return try {
            service.setPackageNetworkingEnabled(uid, enabled)
        } catch (e: Exception) {
            Log.e(TAG, "setPackageNetworkingEnabled threw", e)
            serviceConnected = false
            privilegedService = null
            false
        }
    }

    /**
     * 获取 UserService：先 [Shizuku.peekUserService] 非阻塞探活，未连上再
     * [Shizuku.bindUserService] 并带重试等待。
     */
    private fun getPrivilegedService(): IPrivilegedService? {
        privilegedService?.let { if (serviceConnected && it.asBinder().pingBinder()) return it }

        if (!isShizukuRunning() || !checkSelfPermission()) {
            Log.w(TAG, "Cannot bind UserService: shizuku down or unauthorized")
            return null
        }

        // peekUserService: 探活，返回 binder transact 状态码（0 = 成功）。
        // 服务已在运行时 binder 会立刻通过 serviceConnection 回调，无需等待。
        try {
            val transactionCode = Shizuku.peekUserService(userServiceArgs, serviceConnection)
            if (transactionCode == 0 && serviceConnected) {
                Log.i(TAG, "UserService already running, reused")
                return privilegedService
            }
        } catch (e: Exception) {
            Log.w(TAG, "peekUserService failed", e)
        }

        repeat(BIND_RETRIES) { attempt ->
            val latch = synchronized(bindLock) {
                bindLatch = CountDownLatch(1)
                bindLatch
            }
            try {
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "bindUserService threw (attempt ${attempt + 1})", e)
            }
            val connected = try {
                latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
            if (connected) {
                Log.i(TAG, "Privileged service bound (attempt ${attempt + 1})")
                return privilegedService
            }
            Log.w(TAG, "Bind attempt ${attempt + 1} failed (connected=$connected)")
        }
        return null
    }

    /**
     * Binder 反射兜底：用 [ShizukuBinderWrapper] 包住 ServiceManager 拿到的
     * 原始 binder，再交给 IConnectivityManager$Stub 的 asInterface。
     *
     * 只走 connectivity，**不要**试 network_management：后者在 shell uid 下
     * 恒报 SecurityException: Only available to AID_SYSTEM（HyperOS 4 /
     * Android 17 实测），试它只会浪费一次往返并污染日志。
     *
     * 注意：不能用 SystemServiceHelper.getSystemService(...) —— 那返回的是
     * 客户端代理，再包一层 wrapper 拿不到可用的 interface。
     */
    private fun setPackageNetworkingEnabledViaBinder(uid: Int, enabled: Boolean): Boolean {
        return try {
            val rawBinder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "connectivity") as? IBinder
                ?: run {
                    Log.w(TAG, "connectivity binder not found")
                    return false
                }

            val iface = Class.forName("android.net.IConnectivityManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(rawBinder))

            val setFirewallChainEnabled = iface.javaClass.getMethod(
                "setFirewallChainEnabled",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            setFirewallChainEnabled.invoke(iface, FIREWALL_CHAIN_OEM_DENY, true)

            val rule = if (enabled) 0 else 2 // 0 = ALLOW, 2 = DENY
            val setUidFirewallRule = iface.javaClass.getMethod(
                "setUidFirewallRule",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            setUidFirewallRule.invoke(iface, FIREWALL_CHAIN_OEM_DENY, uid, rule)

            Log.i(TAG, "XMSF via binder[connectivity]: uid=$uid enabled=$enabled")
            true
        } catch (e: Exception) {
            Log.w(TAG, "binder route [connectivity] failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }
}
