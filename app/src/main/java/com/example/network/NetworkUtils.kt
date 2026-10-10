package com.example.network

import java.net.Inet4Address
import java.net.NetworkInterface

data class NetworkAddressInfo(
    val interfaceName: String,
    val displayName: String,
    val ipAddress: String,
    val isWifi: Boolean,
    val isHotspot: Boolean,
    val isEthernet: Boolean,
    val isLoopback: Boolean
)

object NetworkUtils {

    fun getAvailableNetworkAddresses(): List<NetworkAddressInfo> {
        val result = mutableListOf<NetworkAddressInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return result
            for (intf in interfaces) {
                if (!intf.isUp) continue
                val name = intf.name.lowercase()
                val isLoopback = intf.isLoopback
                val isWifi = name.startsWith("wlan") || name.startsWith("wifi")
                val isHotspot = name.startsWith("ap") || name.contains("tether") || name.startsWith("rndis")
                val isEthernet = name.startsWith("eth")

                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        val label = when {
                            isWifi -> "Wi-Fi ($host)"
                            isHotspot -> "Hotspot ($host)"
                            isEthernet -> "Ethernet ($host)"
                            isLoopback -> "Localhost ($host)"
                            else -> "${intf.displayName} ($host)"
                        }
                        result.add(
                            NetworkAddressInfo(
                                interfaceName = intf.name,
                                displayName = label,
                                ipAddress = host,
                                isWifi = isWifi,
                                isHotspot = isHotspot,
                                isEthernet = isEthernet,
                                isLoopback = isLoopback
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort: Wi-Fi/Hotspot/Ethernet first, non-loopback, loopback last
        return result.sortedWith(
            compareBy(
                { it.isLoopback },
                { !it.isWifi && !it.isHotspot },
                { it.displayName }
            )
        )
    }

    fun getPreferredIpAddress(): String {
        val addresses = getAvailableNetworkAddresses()
        val nonLoopback = addresses.firstOrNull { !it.isLoopback }
        return nonLoopback?.ipAddress ?: "127.0.0.1"
    }

    fun formatShareUrl(ipAddress: String, port: Int, shareId: String? = null): String {
        return if (shareId != null) {
            "http://$ipAddress:$port/#/share/$shareId"
        } else {
            "http://$ipAddress:$port/"
        }
    }
}
