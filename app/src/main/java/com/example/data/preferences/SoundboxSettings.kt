package com.example.data.preferences

enum class PaymentChannel(
    val displayName: String,
    val shortCode: String,
    val packageHints: List<String>,
    val keywordHints: List<String>,
) {
    ESEWA(
        displayName = "eSewa",
        shortCode = "eSewa",
        packageHints = listOf("com.f1soft.esewa", "np.com.esewa"),
        keywordHints = listOf("esewa", "e-sewa", "ईसेवा"),
    ),
    KHALTI(
        displayName = "Khalti",
        shortCode = "Khalti",
        packageHints = listOf("com.khalti"),
        keywordHints = listOf("khalti", "खल्ती"),
    ),
    FONEPAY(
        displayName = "Fonepay",
        shortCode = "Fonepay",
        packageHints = listOf("com.f1soft.fonepay", "com.fonepay"),
        keywordHints = listOf("fonepay", "fone pay", "qr payment"),
    ),
    IME_PAY(
        displayName = "IME Pay",
        shortCode = "IME Pay",
        packageHints = listOf("com.swifttechnology.imepay", "com.imepay"),
        keywordHints = listOf("ime pay", "imepay"),
    ),
    CONNECT_IPS(
        displayName = "ConnectIPS",
        shortCode = "cIPS",
        packageHints = listOf("com.nchl.connectips"),
        keywordHints = listOf("connectips", "connect ips"),
    ),
    BANK_ALERT(
        displayName = "Bank & SMS",
        shortCode = "Bank",
        packageHints = listOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            "com.f1soft",
        ),
        keywordHints = listOf(
            "a/c",
            "account",
            "bank",
            "credited",
            "deposited",
            "nabil",
            "nic asia",
            "global ime",
            "sanima",
            "siddhartha",
            "himalayan",
            "prabhu",
            "laxmi",
            "machhapuchchhre",
            "rastriya banijya",
        ),
    ),
    OTHER_WALLET(
        displayName = "Other Wallets & UPI",
        shortCode = "Pay",
        packageHints = listOf(
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "com.moru",
            "com.prabhupay",
        ),
        keywordHints = listOf("gpay", "google pay", "phonepe", "paytm", "upi", "wallet", "moru", "prabhupay"),
    ),
}

enum class VoiceLanguage(
    val languageTag: String,
    val countryTag: String,
    val label: String,
    val sampleCurrencyName: String,
) {
    ENGLISH("en", "US", "English", "Rupees"),
    NEPALI("ne", "NP", "नेपाली (Nepali)", "रुपैयाँ"),
    HINDI("hi", "IN", "हिन्दी (Hindi)", "रुपये"),
}

enum class VoiceSourceMode(val label: String, val description: String) {
    TTS_VOICE("TTS Voice", "Speaks full details using the selected TTS voice"),
    OWN_VOICE_PLUS_AMOUNT("My Voice + Amount", "Plays your recorded voice clip, then speaks amount"),
    OWN_VOICE_ONLY("My Voice Only", "Plays only your custom recorded voice alert"),
}

data class SoundboxSettings(
    val isSpeakerEnabled: Boolean = true,
    val merchantName: String = "",
    val language: VoiceLanguage = VoiceLanguage.ENGLISH,
    val selectedVoiceName: String? = null,
    val voiceSourceMode: VoiceSourceMode = VoiceSourceMode.TTS_VOICE,
    val ownVoiceFilePath: String? = null,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val boostVolume: Boolean = true,
    val playChime: Boolean = true,
    val announceSender: Boolean = true,
    val announceSource: Boolean = true,
    val repeatTwice: Boolean = false,
    val foregroundGuardEnabled: Boolean = false,
    val enabledChannels: Set<PaymentChannel> = PaymentChannel.entries.toSet(),
)
