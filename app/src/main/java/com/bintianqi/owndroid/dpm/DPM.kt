package com.bintianqi.owndroid.dpm

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.ConnectEvent
import android.app.admin.DevicePolicyManager
import android.app.admin.DnsEvent
import android.app.admin.IDevicePolicyManager
import android.app.admin.SecurityLog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.IPackageInstaller
import android.content.pm.PackageInstaller
import android.os.Build.VERSION
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutManagerCompat
import com.bintianqi.owndroid.MyAdminComponent
import com.bintianqi.owndroid.R
import com.bintianqi.owndroid.SharedPrefs
import com.bintianqi.owndroid.createShortcuts
import com.bintianqi.owndroid.myPrivilege
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuBinderWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.OutputStream

val Context.isDeviceOwner: Boolean
    get() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(
            if(SharedPrefs(this).dhizuku) {
                Dhizuku.getOwnerPackageName()
            } else {
                "com.bintianqi.owndroid"
            }
        )
    }

val Context.isProfileOwner: Boolean
    get() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isProfileOwnerApp("com.bintianqi.owndroid")
    }

@SuppressLint("PrivateApi")
fun binderWrapperDevicePolicyManager(appContext: Context): DevicePolicyManager? {
    try {
        val context = appContext.createPackageContext(Dhizuku.getOwnerComponent().packageName, Context.CONTEXT_IGNORE_SECURITY)
        val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val field = manager.javaClass.getDeclaredField("mService")
        field.isAccessible = true
        val oldInterface = field[manager] as IDevicePolicyManager
        if (oldInterface is DhizukuBinderWrapper) return manager
        val oldBinder = oldInterface.asBinder()
        val newBinder = Dhizuku.binderWrapper(oldBinder)
        val newInterface = IDevicePolicyManager.Stub.asInterface(newBinder)
        field[manager] = newInterface
        return manager
    } catch (_: Exception) {
        dhizukuErrorStatus.value = 1
    }
    return null
}

@SuppressLint("PrivateApi")
private fun binderWrapperPackageInstaller(appContext: Context): PackageInstaller? {
    try {
        val context = appContext.createPackageContext(Dhizuku.getOwnerComponent().packageName, Context.CONTEXT_IGNORE_SECURITY)
        val installer = context.packageManager.packageInstaller
        val field = installer.javaClass.getDeclaredField("mInstaller")
        field.isAccessible = true
        val oldInterface = field[installer] as IPackageInstaller
        if (oldInterface is DhizukuBinderWrapper) return installer
        val oldBinder = oldInterface.asBinder()
        val newBinder = Dhizuku.binderWrapper(oldBinder)
        val newInterface = IPackageInstaller.Stub.asInterface(newBinder)
        field[installer] = newInterface
        return installer
    } catch (_: Exception) {
        dhizukuErrorStatus.value = 1
    }
    return null
}

fun Context.getPackageInstaller(): PackageInstaller {
    if(SharedPrefs(this).dhizuku) {
        if (!dhizukuPermissionGranted()) {
            dhizukuErrorStatus.value = 2
            return this.packageManager.packageInstaller
        }
        return binderWrapperPackageInstaller(this) ?: this.packageManager.packageInstaller
    } else {
        return this.packageManager.packageInstaller
    }
}

fun Context.getDPM(): DevicePolicyManager {
    if(SharedPrefs(this).dhizuku) {
        if (!dhizukuPermissionGranted()) {
            dhizukuErrorStatus.value = 2
            return this.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        }
        return binderWrapperDevicePolicyManager(this) ?: this.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    } else {
        return this.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
}

fun Context.getReceiver(): ComponentName {
    return if(SharedPrefs(this).dhizuku) {
        Dhizuku.getOwnerComponent()
    } else {
        MyAdminComponent
    }
}

val dhizukuErrorStatus = MutableStateFlow(0)

data class PermissionItem(
    val permission: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val profileOwnerRestricted: Boolean = false
)

fun permissionList(): List<PermissionItem>{
    val list = mutableListOf<PermissionItem>()
    if(VERSION.SDK_INT >= 33) {
        list.add(PermissionItem(Manifest.permission.POST_NOTIFICATIONS, R.string.permission_POST_NOTIFICATIONS, R.drawable.notifications_fill0))
    }
    list.add(PermissionItem(Manifest.permission.READ_EXTERNAL_STORAGE, R.string.permission_READ_EXTERNAL_STORAGE, R.drawable.folder_fill0))
    list.add(PermissionItem(Manifest.permission.WRITE_EXTERNAL_STORAGE, R.string.permission_WRITE_EXTERNAL_STORAGE, R.drawable.folder_fill0))
    if(VERSION.SDK_INT >= 33) {
        list.add(PermissionItem(Manifest.permission.READ_MEDIA_AUDIO, R.string.permission_READ_MEDIA_AUDIO, R.drawable.music_note_fill0))
        list.add(PermissionItem(Manifest.permission.READ_MEDIA_VIDEO, R.string.permission_READ_MEDIA_VIDEO, R.drawable.movie_fill0))
        list.add(PermissionItem(Manifest.permission.READ_MEDIA_IMAGES, R.string.permission_READ_MEDIA_IMAGES, R.drawable.image_fill0))
    }
    list.add(PermissionItem(Manifest.permission.CAMERA, R.string.permission_CAMERA, R.drawable.photo_camera_fill0, true))
    list.add(PermissionItem(Manifest.permission.RECORD_AUDIO, R.string.permission_RECORD_AUDIO, R.drawable.mic_fill0, true))
    list.add(PermissionItem(Manifest.permission.ACCESS_COARSE_LOCATION, R.string.permission_ACCESS_COARSE_LOCATION, R.drawable.location_on_fill0, true))
    list.add(PermissionItem(Manifest.permission.ACCESS_FINE_LOCATION, R.string.permission_ACCESS_FINE_LOCATION, R.drawable.location_on_fill0, true))
    if(VERSION.SDK_INT >= 29) {
        list.add(PermissionItem(Manifest.permission.ACCESS_BACKGROUND_LOCATION, R.string.permission_ACCESS_BACKGROUND_LOCATION, R.drawable.location_on_fill0, true))
    }
    list.add(PermissionItem(Manifest.permission.READ_CONTACTS, R.string.permission_READ_CONTACTS, R.drawable.contacts_fill0))
    list.add(PermissionItem(Manifest.permission.WRITE_CONTACTS, R.string.permission_WRITE_CONTACTS, R.drawable.contacts_fill0))
    list.add(PermissionItem(Manifest.permission.READ_CALENDAR, R.string.permission_READ_CALENDAR, R.drawable.calendar_month_fill0))
    list.add(PermissionItem(Manifest.permission.WRITE_CALENDAR, R.string.permission_WRITE_CALENDAR, R.drawable.calendar_month_fill0))
    if(VERSION.SDK_INT >= 31) {
        list.add(PermissionItem(Manifest.permission.BLUETOOTH_CONNECT, R.string.permission_BLUETOOTH_CONNECT, R.drawable.bluetooth_fill0))
        list.add(PermissionItem(Manifest.permission.BLUETOOTH_SCAN, R.string.permission_BLUETOOTH_SCAN, R.drawable.bluetooth_searching_fill0))
        list.add(PermissionItem(Manifest.permission.BLUETOOTH_ADVERTISE, R.string.permission_BLUETOOTH_ADVERTISE, R.drawable.bluetooth_fill0))
    }
    if(VERSION.SDK_INT >= 33) {
        list.add(PermissionItem(Manifest.permission.NEARBY_WIFI_DEVICES, R.string.permission_NEARBY_WIFI_DEVICES, R.drawable.wifi_fill0))
    }
    list.add(PermissionItem(Manifest.permission.CALL_PHONE, R.string.permission_CALL_PHONE, R.drawable.call_fill0))
    if(VERSION.SDK_INT >= 26) {
        list.add(PermissionItem(Manifest.permission.ANSWER_PHONE_CALLS, R.string.permission_ANSWER_PHONE_CALLS, R.drawable.call_fill0))
        list.add(PermissionItem(Manifest.permission.READ_PHONE_NUMBERS, R.string.permission_READ_PHONE_STATE, R.drawable.mobile_phone_fill0))
    }
    list.add(PermissionItem(Manifest.permission.READ_PHONE_STATE, R.string.permission_READ_PHONE_STATE, R.drawable.mobile_phone_fill0))
    list.add(PermissionItem(Manifest.permission.USE_SIP, R.string.permission_USE_SIP, R.drawable.call_fill0))
    if(VERSION.SDK_INT >= 31) {
        list.add(PermissionItem(Manifest.permission.UWB_RANGING, R.string.permission_UWB_RANGING, R.drawable.cell_tower_fill0))
    }
    list.add(PermissionItem(Manifest.permission.READ_SMS, R.string.permission_READ_SMS, R.drawable.sms_fill0))
    list.add(PermissionItem(Manifest.permission.RECEIVE_SMS, R.string.permission_RECEIVE_SMS, R.drawable.sms_fill0))
    list.add(PermissionItem(Manifest.permission.SEND_SMS, R.string.permission_SEND_SMS, R.drawable.sms_fill0))
    list.add(PermissionItem(Manifest.permission.READ_CALL_LOG, R.string.permission_READ_CALL_LOG, R.drawable.call_log_fill0))
    list.add(PermissionItem(Manifest.permission.WRITE_CALL_LOG, R.string.permission_WRITE_CALL_LOG, R.drawable.call_log_fill0))
    list.add(PermissionItem(Manifest.permission.RECEIVE_WAP_PUSH, R.string.permission_RECEIVE_WAP_PUSH, R.drawable.wifi_fill0))
    list.add(PermissionItem(Manifest.permission.BODY_SENSORS, R.string.permission_BODY_SENSORS, R.drawable.sensors_fill0, true))
    if(VERSION.SDK_INT >= 33) {
        list.add(PermissionItem(Manifest.permission.BODY_SENSORS_BACKGROUND, R.string.permission_BODY_SENSORS_BACKGROUND, R.drawable.sensors_fill0))
    }
    if(VERSION.SDK_INT > 29) {
        list.add(PermissionItem(Manifest.permission.ACTIVITY_RECOGNITION, R.string.permission_ACTIVITY_RECOGNITION, R.drawable.history_fill0, true))
    }
    return list
}

@RequiresApi(26)
fun handleNetworkLogs(context: Context, batchToken: Long) {
    val networkEvents = context.getDPM().retrieveNetworkLogs(context.getReceiver(), batchToken) ?: return
    val file = context.filesDir.resolve("NetworkLogs.json")
    val fileExist = file.exists()
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val buffer = file.bufferedWriter()
    networkEvents.forEachIndexed { index, event ->
        if(fileExist && index == 0) buffer.write(",")
        val item = buildJsonObject {
            if(VERSION.SDK_INT >= 28) put("id", event.id)
            put("time", event.timestamp)
            put("package", event.packageName)
            if(event is DnsEvent) {
                put("type", "dns")
                put("host", event.hostname)
                put("count", event.totalResolvedAddressCount)
                putJsonArray("addresses") {
                    event.inetAddresses.forEach { inetAddresses ->
                        add(inetAddresses.hostAddress)
                    }
                }
            }
            if(event is ConnectEvent) {
                put("type", "connect")
                put("address", event.inetAddress.hostAddress)
                put("port", event.port)
            }
        }
        buffer.write(json.encodeToString(item))
        if(index < networkEvents.size - 1) buffer.write(",")
    }
    buffer.close()
}

@RequiresApi(24)
fun processSecurityLogs(securityEvents: List<SecurityLog.SecurityEvent>, outputStream: OutputStream) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val buffer = outputStream.bufferedWriter()
    securityEvents.forEachIndexed { index, event ->
        val item = buildJsonObject {
            put("time", event.timeNanos / 1000)
            put("tag", event.tag)
            if(VERSION.SDK_INT >= 28) put("level", event.logLevel)
            if(VERSION.SDK_INT >= 28) put("id", event.id)
            parseSecurityEventData(event).let { if(it != null) put("data", it) }
        }
        buffer.write(json.encodeToString(item))
        if(index < securityEvents.size - 1) buffer.write(",")
    }
    buffer.close()
}

@RequiresApi(24)
fun parseSecurityEventData(event: SecurityLog.SecurityEvent): JsonElement? {
    return when(event.tag) {
        SecurityLog.TAG_ADB_SHELL_CMD -> JsonPrimitive(event.data as String)
        SecurityLog.TAG_ADB_SHELL_INTERACTIVE -> null
        SecurityLog.TAG_APP_PROCESS_START -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("name", payload[0] as String)
                put("time", payload[1] as Long)
                put("uid", payload[2] as Int)
                put("pid", payload[3] as Int)
                put("seinfo", payload[4] as String)
                put("apk_hash", payload[5] as String)
            }
        }
        SecurityLog.TAG_BACKUP_SERVICE_TOGGLED -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("admin", payload[0] as String)
                put("admin_user_id", payload[1] as Int)
                put("state", payload[2] as Int)
            }
        }
        SecurityLog.TAG_BLUETOOTH_CONNECTION -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("mac", payload[0] as String)
                put("successful", payload[1] as Int)
                (payload[2] as String).let { if(it != "") put("failure_reason", it) }
            }
        }
        SecurityLog.TAG_BLUETOOTH_DISCONNECTION -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("mac", payload[0] as String)
                (payload[1] as String).let { if(it != "") put("reason", it) }
            }
        }
        SecurityLog.TAG_CAMERA_POLICY_SET -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("admin", payload[0] as String)
                put("admin_user_id", payload[1] as Int)
                put("target_user_id", payload[2] as Int)
                put("disabled", payload[3] as Int)
            }
        }
        SecurityLog.TAG_CERT_AUTHORITY_INSTALLED, SecurityLog.TAG_CERT_AUTHORITY_REMOVED -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("result", payload[0] as Int)
                put("subject", payload[1] as String)
                if(VERSION.SDK_INT >= 30) put("user", payload[2] as Int)
            }
        }
        SecurityLog.TAG_CERT_VALIDATION_FAILURE -> JsonPrimitive(event.data as String)
        SecurityLog.TAG_CRYPTO_SELF_TEST_COMPLETED -> JsonPrimitive(event.data as Int)
        SecurityLog.TAG_KEYGUARD_DISABLED_FEATURES_SET -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("admin", payload[0] as String)
                put("admin_user_id", payload[1] as Int)
                put("target_user_id", payload[2] as Int)
                put("mask", payload[3] as Int)
            }
        }
        SecurityLog.TAG_KEYGUARD_DISMISSED -> null
        SecurityLog.TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("result", payload[0] as Int)
                put("strength", payload[1] as Int)
            }
        }
        SecurityLog.TAG_KEYGUARD_SECURED -> null
        SecurityLog.TAG_KEY_DESTRUCTION, SecurityLog.TAG_KEY_GENERATED, SecurityLog.TAG_KEY_IMPORT -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("result", payload[0] as Int)
                put("alias", payload[1] as String)
                put("uid", payload[2] as Int)
            }
        }
        SecurityLog.TAG_KEY_INTEGRITY_VIOLATION -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("alias", payload[0] as String)
                put("uid", payload[1] as Int)
            }
        }
        SecurityLog.TAG_LOGGING_STARTED, SecurityLog.TAG_LOGGING_STOPPED -> null
        SecurityLog.TAG_LOG_BUFFER_SIZE_CRITICAL -> null
        SecurityLog.TAG_MAX_PASSWORD_ATTEMPTS_SET -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("admin", payload[0] as String)
                put("admin_user_id", payload[1] as Int)
                put("target_user_id", payload[2] as Int)
                put("value", payload[3] as Int)
            }
        }
        SecurityLog.TAG_MAX_SCREEN_LOCK_TIMEOUT_SET -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("admin", payload[0] as String)
                put("admin_user_id", payload[1] as Int)
                put("target_user_id", payload[2] as Int)
                put("timeout", payload[3] as Long)
            }
        }
        SecurityLog.TAG_MEDIA_MOUNT, SecurityLog.TAG_MEDIA_UNMOUNT -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("mount_point", payload[0] as String)
                put("volume_label", payload[1] as String)
            }
        }
        SecurityLog.TAG_OS_SHUTDOWN -> null
        SecurityLog.TAG_OS_STARTUP -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("verified_boot_state", payload[0] as String)
                put("dm_verify_state", payload[1] as String)
            }
        }
        SecurityLog.TAG_PACKAGE_INSTALLED, SecurityLog.TAG_PACKAGE_UNINSTALLED, SecurityLog.TAG_PACKAGE_UPDATED -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("name", payload[0] as String)
                put("version", payload[1] as Long)
                put("user_id", payload[2] as Int)
            }
        }
        SecurityLog.TAG_PASSWORD_CHANGED -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("complexity", payload[0] as Int)
                put("user_id", payload[1] as Int)
            }
        }
        SecurityLog. TAG_PASSWORD_COMPLEXITY_REQUIRED -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("admin", payload[0] as String)
                put("admin_user_id", payload[1] as Int)
                put("target_user_id", payload[2] as Int)
                put("complexity", payload[3] as Int)
            }
        }
        SecurityLog.TAG_PASSWORD_COMPLEXITY_SET -> null //Deprecated
        SecurityLog.TAG_PASSWORD_EXPIRATION_SET -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("admin", payload[0] as String)
                put("admin_user_id", payload[1] as Int)
                put("target_user_id", payload[2] as Int)
                put("timeout", payload[3] as Long)
            }
        }
        SecurityLog.TAG_PASSWORD_HISTORY_LENGTH_SET -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("admin", payload[0] as String)
                put("admin_user_id", payload[1] as Int)
                put("target_user_id", payload[2] as Int)
                put("length", payload[3] as Int)
            }
        }
        SecurityLog.TAG_REMOTE_LOCK -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("admin", payload[0] as String)
                put("admin_user_id", payload[1] as Int)
                put("target_user_id", payload[2] as Int)
            }
        }
        SecurityLog.TAG_SYNC_RECV_FILE, SecurityLog.TAG_SYNC_SEND_FILE -> JsonPrimitive(event.data as String)
        SecurityLog.TAG_USER_RESTRICTION_ADDED, SecurityLog.TAG_USER_RESTRICTION_REMOVED -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("admin", payload[0] as String)
                put("admin_user_id", payload[1] as Int)
                put("restriction", payload[2] as String)
            }
        }
        SecurityLog.TAG_WIFI_CONNECTION -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("bssid", payload[0] as String)
                put("type", payload[1] as String)
                (payload[2] as String).let { if(it != "") put("failure_reason", it) }
            }
        }
        SecurityLog.TAG_WIFI_DISCONNECTION -> {
            val payload = event.data as Array<*>
            buildJsonObject {
                put("bssid", payload[0] as String)
                (payload[1] as String).let { if(it != "") put("reason", it) }
            }
        }
        SecurityLog.TAG_WIPE_FAILURE -> null
        else -> null
    }
}

fun setDefaultAffiliationID(context: Context) {
    if(VERSION.SDK_INT < 26) return
    val sp = SharedPrefs(context)
    val privilege = myPrivilege.value
    if(!sp.isDefaultAffiliationIdSet) {
        try {
            if(privilege.device || (!privilege.primary && privilege.profile)) {
                val dpm = context.getDPM()
                val receiver = context.getReceiver()
                val affiliationIDs = dpm.getAffiliationIds(receiver)
                if(affiliationIDs.isEmpty()) {
                    dpm.setAffiliationIds(receiver, setOf("OwnDroid_default_affiliation_id"))
                    sp.isDefaultAffiliationIdSet = true
                    Log.d("DPM", "Default affiliation id set")
                }
            }
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }
}

fun dhizukuPermissionGranted() =
    try {
        Dhizuku.isPermissionGranted()
    } catch(_: Exception) {
        false
    }

fun parsePackageInstallerMessage(context: Context, result: Intent): String {
    val status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, 999)
    val statusMessage = result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
    val otherPackageName = result.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME)
    return when(status) {
        PackageInstaller.STATUS_FAILURE_BLOCKED ->
            context.getString(
                R.string.status_failure_blocked,
                otherPackageName ?: context.getString(R.string.unknown)
            )
        PackageInstaller.STATUS_FAILURE_ABORTED ->
            context.getString(R.string.status_failure_aborted)
        PackageInstaller.STATUS_FAILURE_INVALID ->
            context.getString(R.string.status_failure_invalid)
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            context.getString(R.string.status_failure_conflict, otherPackageName ?: "???")
        PackageInstaller.STATUS_FAILURE_STORAGE ->
            context.getString(R.string.status_failure_storage) +
                    result.getStringExtra(PackageInstaller.EXTRA_STORAGE_PATH).let { if(it == null) "" else "\n$it" }
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            context.getString(R.string.status_failure_incompatible)
        PackageInstaller.STATUS_FAILURE_TIMEOUT ->
            context.getString(R.string.timeout)
        else -> ""
    } + statusMessage.let { if(it == null) "" else "\n$it" }
}


fun handlePrivilegeChange(context: Context) {
    val privilege = myPrivilege.value
    val activated = privilege.device || privilege.profile
    val sp = SharedPrefs(context)
    sp.dhizukuServer = false
    if(activated) {
        createShortcuts(context)
        if(!privilege.dhizuku) {
            setDefaultAffiliationID(context)
        }
    } else {
        sp.isDefaultAffiliationIdSet = false
        if(VERSION.SDK_INT >= 25) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        }
        sp.isApiEnabled = false
    }
}
