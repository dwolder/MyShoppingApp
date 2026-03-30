package com.myshoppinglist.data.local.entity

data class StoreInfo(
    val id: String,
    val displayName: String,
    val apiId: String,
    val listType: ListType
) {
    companion object {
        val ALL_STORES = listOf(
            StoreInfo("superstore", "Superstore", "superstore", ListType.GROCERY),
            StoreInfo("metro", "Metro", "metro", ListType.GROCERY),
            StoreInfo("freshco", "FreshCo", "freshco", ListType.GROCERY),
            StoreInfo("sobeys", "Sobeys", "sobeys", ListType.GROCERY),
            StoreInfo("homedepot", "Home Depot", "homedepot", ListType.HOME_IMPROVEMENT),
            StoreInfo("rona", "Rona", "rona", ListType.HOME_IMPROVEMENT),
        )

        fun forListType(listType: ListType): List<StoreInfo> =
            ALL_STORES.filter { it.listType == listType }

        fun fromApiId(apiId: String): StoreInfo? =
            ALL_STORES.find { it.apiId == apiId }
    }
}
