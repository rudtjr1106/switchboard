package io.github.rudtjr1106.switchboard.config

object Fixtures {
    fun resource(path: String): String =
        checkNotNull(Fixtures::class.java.getResourceAsStream("/$path")) { "missing test resource $path" }
            .readBytes().decodeToString()

    val androidSchemaText: String get() = resource("umc-android/schema.json")
    val androidConfigText: String get() = resource("umc-android/app-config.json")
    val iosSchemaText: String get() = resource("umc-ios/schema.json")
    val iosConfigText: String get() = resource("umc-ios/app-config.json")

    val androidSchema: ConfigSchema get() = ConfigSchema.parse(androidSchemaText)
    val iosSchema: ConfigSchema get() = ConfigSchema.parse(iosSchemaText)
}
