package com.chayewuu.hypermatter.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import rikka.sui.Sui
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Shizuku 接入（移植自 NexioSchedule）：
 * - Sui.init + Shizuku.pingBinder 探测可用性
 * - requestPermission 拉起 Shizuku 授权
 * - setXmsfNetworkingEnabled: 优先 UserService（IPrivilegedService），
 *   失败降级为直连 binder 反射（IConnectivityManager.setFirewallChainEnabled
 *   + setUidFirewallRule，OEM deny 链 + uid 规则）
 */
object ShizukuManager {
    private const val TAG = "ShizukuManager"
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    private const val FIREWALL_CHAIN_OEM_DENY = 9

    private var initialized = false
    private var privilegedService: IPrivilegedService? = null
    private var serviceConnected = false
    private var bindLatch = CountDownLatch(1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                privilegedService = IPrivilegedService.Stub.asInterface(binder)
                serviceConnected = true
                bindLatch.countDown()
                Log.d(TAG, "Privileged service connected")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            privilegedService = null
            serviceConnected = false
            bindLatch = CountDownLatch(1)
            Log.d(TAG, "Privileged service disconnected")
        }
    }

    fun init(context: Context) {
        if (initialized) return
        try {
            Sui.init(context.packageName)
            initialized = true
            Log.d(TAG, "Shizuku initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Shizuku", e)
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
     */
    fun setXmsfNetworkingEnabled(context: Context, enabled: Boolean): Boolean {
        init(context)
        if (!isShizukuRunning() || !checkSelfPermission()) {
            Log.w(TAG, "Shizuku not available or no permission")
            return false
        }

        return try {
            val xmsfUid = context.packageManager.getPackageUid(XMSF_PACKAGE, 0)
            setPackageNetworkingEnabledViaService(xmsfUid, enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set networking via service, trying fallback", e)
            try {
                val xmsfUid = context.packageManager.getPackageUid(XMSF_PACKAGE, 0)
                setPackageNetworkingEnabledViaBinder(xmsfUid, enabled)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to set networking via binder fallback", e2)
                false
            }
        }
    }

    private fun setPackageNetworkingEnabledViaService(uid: Int, enabled: Boolean): Boolean {
        val service = getPrivilegedService() ?: return false
        return service.setPackageNetworkingEnabled(uid, enabled)
    }

    private fun getPrivilegedService(): IPrivilegedService? {
        if (privilegedService != null && serviceConnected) {
            return privilegedService
        }

        return try {
            bindLatch = CountDownLatch(1)

            val args = Shizuku.UserServiceArgs(
                ComponentName("com.chayewuu.hypermatter", PrivilegedServiceImpl::class.java.name),
            )
                .daemon(false)
                .processNameSuffix("privileged")
                .debuggable(false)
                .version(1)

            Shizuku.bindUserService(args, serviceConnection)

            val connected = bindLatch.await(3, TimeUnit.SECONDS)
            if (connected) {
                Log.d(TAG, "Privileged service bound successfully")
            } else {
                Log.w(TAG, "Privileged service bind timed out")
            }

            privilegedService
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind privileged service", e)
            null
        }
    }

    private fun setPackageNetworkingEnabledViaBinder(uid: Int, enabled: Boolean): Boolean {
        return try {
            val connectivityManager = SystemServiceHelper.getSystemService("connectivity")
                ?: return false

            val wrappedBinder = ShizukuBinderWrapper(connectivityManager)
            val iConnectivityManager = Class.forName("android.net.IConnectivityManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, wrappedBinder)

            val setFirewallChainEnabled = iConnectivityManager.javaClass.getMethod(
                "setFirewallChainEnabled",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            setFirewallChainEnabled.invoke(iConnectivityManager, FIREWALL_CHAIN_OEM_DENY, true)

            val rule = if (enabled) 0 else 2 // 0 = ALLOW, 2 = DENY
            val setUidFirewallRule = iConnectivityManager.javaClass.getMethod(
                "setUidFirewallRule",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            setUidFirewallRule.invoke(iConnectivityManager, FIREWALL_CHAIN_OEM_DENY, uid, rule)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set networking via binder", e)
            false
        }
    }
}
