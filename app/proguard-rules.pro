# Not "intentionally minimal" anymore — CI now runs a real, minified
# assembleRelease (signed, see android-release-apk.yml), so this file has
# real effect on the shipped artifact for the first time. The comment
# below described the pre-signing state; kept here as history, not
# current fact.
-keepattributes Signature,InnerClasses,*Annotation*

# kotlinx.serialization generates synthetic serializer classes per
# @Serializable type; R8 must not strip or rename them.
-keepclasseswithmembers class com.vault.app.data.remote.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.vault.app.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# androidx.security:security-crypto (EncryptedSharedPreferences — see
# SessionManager.kt) transitively pulls in Google Tink, which references
# com.google.errorprone.annotations.* (CanIgnoreReturnValue,
# CheckReturnValue, Immutable, RestrictedApi, ...) for compile-time-only
# static-analysis annotations — never a real runtime dependency, but R8
# treats any referenced-but-missing class as a hard error by default. This
# is confirmed via a real minifyReleaseWithR8 failure, not a preemptive
# guess: the first real signed release build hit exactly this, since
# isMinifyEnabled was false everywhere before (debug builds never
# minify), so this gap was never exercised until now. Wildcarded across
# the whole package rather than the 4 specific classes R8 happened to
# report this run — Tink may reference others R8 hasn't surfaced yet.
-dontwarn com.google.errorprone.annotations.**
