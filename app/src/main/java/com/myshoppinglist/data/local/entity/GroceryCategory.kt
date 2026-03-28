package com.myshoppinglist.data.local.entity

enum class GroceryCategory(val displayName: String, val sortOrder: Int) {
    PRODUCE("Produce", 0),
    DAIRY("Dairy", 1),
    MEAT("Meat & Seafood", 2),
    BAKERY("Bakery", 3),
    DELI("Deli", 4),
    FROZEN("Frozen", 5),
    CANNED("Canned & Jarred", 6),
    SNACKS("Snacks", 7),
    BEVERAGES("Beverages", 8),
    CONDIMENTS("Condiments & Sauces", 9),
    PASTA("Pasta & Grains", 10),
    CEREAL("Cereal & Breakfast", 11),
    BAKING("Baking", 12),
    SPICES("Spices & Seasonings", 13),
    HOUSEHOLD("Household", 14),
    PERSONAL("Personal Care", 15),
    BABY("Baby", 16),
    PET("Pet", 17),
    OTHER("Other", 18);

    companion object {
        fun fromDisplayName(name: String): GroceryCategory {
            return entries.find {
                it.displayName.equals(name, ignoreCase = true)
            } ?: OTHER
        }
    }
}
