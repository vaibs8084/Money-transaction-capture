package com.vaibhav.moneycapture

data class TransactionPayload(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val notificationKey: String
)
