package com.pft.financetracker.domain.model

enum class TransactionType { DEBIT, CREDIT }

/**
 * What a transaction means for your money, independent of debit/credit direction.
 * Only EXPENSE (and CASH, by default) count as "spend". REFUND reduces spend. INCOME is real income.
 * TRANSFER and INVESTMENT move money between your own pockets and are never counted as spend or income.
 */
enum class Flow(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    REFUND("Refund / cashback"),
    TRANSFER("Transfer"),
    INVESTMENT("Investment"),
    CASH("Cash withdrawal"),
    SETTLEMENT("Split settlement");

    companion object {
        fun fromName(name: String?): Flow? = entries.firstOrNull { it.name == name }
    }
}

/** Money helpers. All amounts are stored as whole paise (Long) so sums never drift. */
object Money {
    fun toPaise(rupees: Double): Long = Math.round(rupees * 100.0)
    fun toRupees(paise: Long): Double = paise / 100.0
    fun parsePaise(text: String): Long? {
        val cleaned = text.replace(",", "").replace("₹", "").trim()
        if (cleaned.isEmpty()) return null
        val d = cleaned.toDoubleOrNull() ?: return null
        if (d.isNaN() || d.isInfinite() || d < 0) return null
        return toPaise(d)
    }
}

/**
 * Categories with keyword lists used by the rule-based categorizer.
 * Add keywords here to improve auto-categorization; no other code changes required.
 */
enum class Category(val label: String, val keywords: List<String>) {
    FOOD(
        "Food & Dining",
        listOf(
            "swiggy", "zomato", "dominos", "pizza", "kfc", "mcdonald", "burger", "cafe", "coffee", "starbucks",
            "restaurant", "dine", "food", "biryani", "dunkin", "subway", "haldiram", "bakery", "chai", "kitchen",
            "dhaba", "eatsure", "box8", "faasos", "behrouz", "blinkit", "zepto", "instamart", "bigbasket", "grofers",
            "dmart", "grocery", "kirana", "milk", "dairy", "supermarket"
        )
    ),
    SHOPPING(
        "Shopping",
        listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "snapdeal", "tata cliq", "trends", "zara",
            "lifestyle", "shoppers", "decathlon", "croma", "vijay sales", "ikea", "pantaloons", "westside", "boat",
            "lenskart", "store", "shop", "mall", "retail", "purchase"
        )
    ),
    BILLS(
        "Bills & Utilities",
        listOf(
            "electricity", "bescom", "msedcl", "tneb", "tata power", "adani", "torrent", "bses", "water", "gas",
            "indane", "hp gas", "bharat gas", "broadband", "airtel", "jio", "vodafone", "bsnl", "act fibernet",
            "hathway", "tata sky", "dish tv", "d2h", "recharge", "postpaid", "prepaid", "bill", "dth", "wifi", "fibre",
            "fiber", "tata play", "insurance", "lic", "premium", "emi", "loan", "rent", "maintenance", "society"
        )
    ),
    TRANSPORT(
        "Transport",
        listOf(
            "uber", "ola", "rapido", "metro", "irctc", "railway", "redbus", "abhibus", "indigo", "air india",
            "spicejet", "vistara", "akasa", "goibibo", "makemytrip", "yatra", "cleartrip", "ixigo", "petrol",
            "diesel", "fuel", "hpcl", "bpcl", "iocl", "indian oil", "bharat petroleum", "toll", "fastag", "parking",
            "cab", "taxi", "namma yatri", "blusmart", "zoomcar"
        )
    ),
    ENTERTAINMENT(
        "Entertainment",
        listOf(
            "netflix", "prime video", "hotstar", "disney", "spotify", "youtube", "gaana", "wynk", "jiosaavn",
            "sony liv", "sonyliv", "zee5", "bookmyshow", "pvr", "inox", "cinepolis", "cinema", "movie", "steam",
            "playstation", "xbox", "apple", "google play", "play store", "dream11", "mpl", "subscription"
        )
    ),
    HEALTH(
        "Health",
        listOf(
            "pharmacy", "pharmeasy", "1mg", "netmeds", "apollo", "medplus", "hospital", "clinic", "doctor", "lab",
            "diagnostic", "thyrocare", "practo", "cultfit", "cult.fit", "gym", "fitness", "medical", "medicine",
            "dental", "health"
        )
    ),
    EDUCATION(
        "Education",
        listOf(
            "udemy", "coursera", "byju", "unacademy", "vedantu", "school", "college", "university", "tuition",
            "course", "exam", "fees", "kindle", "physicswallah", "upgrad"
        )
    ),
    INVESTMENT(
        "Investments",
        listOf(
            "zerodha", "groww", "upstox", "kuvera", "mutual fund", "sip", "icclearing", "indian clearing", "cdsl",
            "nsdl", "ppf", "nps", "fixed deposit", "smallcase", "etmoney", "paytm money", "angel one", "5paisa"
        )
    ),
    ATM("Cash / ATM", listOf("atm", "cash wdl", "cash withdrawal", "cwdr", "cash")),
    TRANSFER("Transfers", listOf("neft", "imps", "rtgs", "self transfer", "own account", "add money", "wallet", "credit card bill", "card bill", "cc payment", "card payment", "cred club", "cred.club", "billdesk")),
    INCOME("Income", listOf("salary", "payroll", "interest", "dividend", "bonus", "stipend")),
    OTHER("Other", emptyList());

    companion object {
        fun fromName(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHER
        val spendCategories: List<Category> get() = entries.filter { it != INCOME }
    }
}

data class Transaction(
    val id: Long = 0,
    /** Amount in paise. Use [amount] for a rupee Double when formatting. */
    val amountPaise: Long,
    val type: TransactionType,
    val merchant: String,
    val category: Category,
    val timestamp: Long,
    val bankName: String?,
    val accountRef: String?,
    val source: Source,
    val flow: Flow,
    val note: String? = null,
    val smsHash: String? = null,
    /** Bank/UPI reference number when the SMS had one. Used to catch the same payment reported by two senders. */
    val refNumber: String? = null,
    val confidence: Int = 100,
    val needsReview: Boolean = false,
    /** Bank-reported amount before a split shrank this row to my share; null when never shrunk. */
    val originalAmountPaise: Long? = null,
    /** A person corrected this row; automatic processes must leave it alone. */
    val userEdited: Boolean = false,
) {
    enum class Source { SMS, MANUAL, SPLIT }
    val amount: Double get() = Money.toRupees(amountPaise)
}

data class Budget(
    val category: Category,
    val monthlyLimitPaise: Long,
) {
    val monthlyLimit: Double get() = Money.toRupees(monthlyLimitPaise)
}
