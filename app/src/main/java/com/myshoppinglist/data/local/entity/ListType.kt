package com.myshoppinglist.data.local.entity

enum class ListType(val displayName: String, val icon: String) {
    GROCERY("Grocery", "shopping_cart"),
    HOME_IMPROVEMENT("Home Improvement", "hardware"),
    GENERAL("General", "list");

    companion object {
        fun fromName(name: String): ListType {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: GENERAL
        }
    }
}
