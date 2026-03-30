package com.myshoppinglist.data.local.entity

enum class GroceryCategory(val displayName: String, val sortOrder: Int, val listType: ListType? = null) {
    // Grocery categories
    PRODUCE("Produce", 0, ListType.GROCERY),
    DAIRY("Dairy", 1, ListType.GROCERY),
    MEAT("Meat & Seafood", 2, ListType.GROCERY),
    BAKERY("Bakery", 3, ListType.GROCERY),
    DELI("Deli", 4, ListType.GROCERY),
    FROZEN("Frozen", 5, ListType.GROCERY),
    CANNED("Canned & Jarred", 6, ListType.GROCERY),
    SNACKS("Snacks", 7, ListType.GROCERY),
    BEVERAGES("Beverages", 8, ListType.GROCERY),
    CONDIMENTS("Condiments & Sauces", 9, ListType.GROCERY),
    PASTA("Pasta & Grains", 10, ListType.GROCERY),
    CEREAL("Cereal & Breakfast", 11, ListType.GROCERY),
    BAKING("Baking", 12, ListType.GROCERY),
    SPICES("Spices & Seasonings", 13, ListType.GROCERY),
    HOUSEHOLD("Household", 14, ListType.GROCERY),
    PERSONAL("Personal Care", 15, ListType.GROCERY),
    BABY("Baby", 16, ListType.GROCERY),
    PET("Pet", 17, ListType.GROCERY),

    // Home Improvement categories
    LUMBER("Lumber & Building", 20, ListType.HOME_IMPROVEMENT),
    PAINT("Paint & Stain", 21, ListType.HOME_IMPROVEMENT),
    ELECTRICAL("Electrical", 22, ListType.HOME_IMPROVEMENT),
    PLUMBING("Plumbing", 23, ListType.HOME_IMPROVEMENT),
    TOOLS("Tools", 24, ListType.HOME_IMPROVEMENT),
    HARDWARE("Hardware & Fasteners", 25, ListType.HOME_IMPROVEMENT),
    FLOORING("Flooring", 26, ListType.HOME_IMPROVEMENT),
    KITCHEN_BATH("Kitchen & Bath", 27, ListType.HOME_IMPROVEMENT),
    LIGHTING("Lighting", 28, ListType.HOME_IMPROVEMENT),
    OUTDOOR("Outdoor & Garden", 29, ListType.HOME_IMPROVEMENT),
    APPLIANCES("Appliances", 30, ListType.HOME_IMPROVEMENT),
    STORAGE("Storage & Organization", 31, ListType.HOME_IMPROVEMENT),

    // Universal
    OTHER("Other", 99, null);

    companion object {
        fun fromDisplayName(name: String): GroceryCategory {
            return entries.find {
                it.displayName.equals(name, ignoreCase = true)
            } ?: OTHER
        }

        fun forListType(listType: ListType): List<GroceryCategory> {
            return entries.filter { it.listType == listType || it == OTHER }
        }
    }
}
