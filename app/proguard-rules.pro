
# Strip verbose/debug logging (and its string building) from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
