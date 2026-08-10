package com.olympussurge.game.legal

/** One block of a legal document: a heading with prose and/or a list. */
data class LegalSection(
    val title: String,
    val body: List<String> = emptyList(),
    val bullets: List<String> = emptyList(),
)

/**
 * The privacy policy and support details, shipped inside the app.
 *
 * They are Kotlin constants rather than a fetched page because the game is
 * offline-first: a player must be able to read what is collected and how to
 * reach us with the plane in the air. The wording mirrors the published policy
 * at olymmpussurge.com so the two can never disagree.
 */
object LegalTexts {

    const val SUPPORT_EMAIL = "support@olymmpussurge.com"
    const val PRIVACY_EFFECTIVE = "Effective June 2026"

    val privacy: List<LegalSection> = listOf(
        LegalSection(
            title = "OVERVIEW",
            body = listOf(
                "The developer (\"we\", \"us\", \"our\") operates the Olympus Surge " +
                    "mobile application (\"Service\"). This policy explains how " +
                    "information is collected, used and protected when you use it.",
            ),
        ),
        LegalSection(
            title = "INFORMATION WE COLLECT",
            body = listOf(
                "The Service may collect limited technical information necessary " +
                    "for its operation and improvement:",
            ),
            bullets = listOf(
                "Device type and model",
                "Operating system version",
                "Anonymous usage statistics",
                "Diagnostic and crash information",
                "IP address, where required for security and analytics",
            ),
        ),
        LegalSection(
            title = "WHAT WE NEVER COLLECT",
            body = listOf(
                "We do not intentionally collect sensitive personal information " +
                    "such as financial account details, government-issued " +
                    "identification numbers or biometric data.",
            ),
        ),
        LegalSection(
            title = "HOW WE USE INFORMATION",
            bullets = listOf(
                "Provide and maintain the Service",
                "Improve functionality and player experience",
                "Monitor performance and stability",
                "Detect, prevent and resolve technical issues",
                "Comply with legal obligations",
            ),
        ),
        LegalSection(
            title = "STORAGE AND SECURITY",
            body = listOf(
                "We take reasonable measures to protect information from " +
                    "unauthorised access, alteration, disclosure or destruction. " +
                    "No method of electronic transmission or storage is completely " +
                    "secure.",
                "Your progress in this build — essence, upgrades, records and " +
                    "profile — is written only to this device.",
            ),
        ),
        LegalSection(
            title = "THIRD-PARTY SERVICES",
            body = listOf(
                "The Service may use third-party providers for analytics, crash " +
                    "reporting, hosting or other operational purposes. They process " +
                    "information solely on our behalf.",
            ),
        ),
        LegalSection(
            title = "DATA RETENTION",
            body = listOf(
                "Information is kept only as long as needed to provide the Service, " +
                    "comply with legal obligations, resolve disputes and enforce " +
                    "agreements.",
            ),
        ),
        LegalSection(
            title = "DATA DELETION",
            body = listOf(
                "You may request deletion of your personal data by writing to " +
                    "$SUPPORT_EMAIL. Include enough detail to identify your account " +
                    "or device; verified requests are handled within a reasonable " +
                    "timeframe.",
                "Because this game stores data on your device only, Settings \u2192 " +
                    "Reset progress erases everything it has saved, and uninstalling " +
                    "the app removes the rest.",
            ),
        ),
        LegalSection(
            title = "YOUR RIGHTS",
            body = listOf(
                "Depending on where you live you may have rights of access, " +
                    "correction, deletion, restriction or portability under " +
                    "applicable privacy laws, including the GDPR.",
            ),
        ),
        LegalSection(
            title = "CHILDREN'S PRIVACY",
            body = listOf(
                "The Service is not intended for children under 18 and we do not " +
                    "knowingly collect personal information from them.",
            ),
        ),
        LegalSection(
            title = "CHANGES TO THIS POLICY",
            body = listOf(
                "This policy may be updated from time to time. Changes take effect " +
                    "when published, both in the app and at olymmpussurge.com.",
            ),
        ),
        LegalSection(
            title = "CONTACT",
            body = listOf("Olympus Surge \u00B7 $SUPPORT_EMAIL"),
        ),
    )

    val support: List<LegalSection> = listOf(
        LegalSection(
            title = "CONTACT",
            body = listOf(
                "Write to $SUPPORT_EMAIL with any question, bug or request. " +
                    "Replies usually go out within a few days.",
            ),
        ),
        LegalSection(
            title = "WHAT TO INCLUDE",
            body = listOf("A reply comes faster when the message says:"),
            bullets = listOf(
                "What happened, and what you expected instead",
                "The arena and wave it happened on",
                "Whether it happens every time",
                "Your phone model and Android version",
            ),
        ),
        LegalSection(
            title = "PROGRESS AND DATA",
            body = listOf(
                "The game plays fully offline and saves everything on this device " +
                    "only. Settings \u2192 Reset progress erases every run, upgrade " +
                    "and record; uninstalling the app removes the rest.",
            ),
        ),
    )
}
