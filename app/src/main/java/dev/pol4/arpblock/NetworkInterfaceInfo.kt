package dev.pol4.arpblock

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NetworkInterfaceInfo(
    val name: String,
    val mac: String,
    val ip: String,
    val subnet: String,
    val gateway: String?
) : Parcelable
