# Intentionally minimal — the CI-built debug variant never runs R8
# (isMinifyEnabled = false), so this file has no effect on the artifact
# GitHub Actions produces. It exists so `assembleRelease` isn't left with
# a dangling proguardFiles() reference to a missing file, should that
# variant ever get configured for real (see README "Signed release").
-keepattributes Signature,InnerClasses,*Annotation*

# kotlinx.serialization generates synthetic serializer classes per
# @Serializable type; R8 must not strip or rename them.
-keepclasseswithmembers class com.vault.app.data.remote.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.vault.app.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
