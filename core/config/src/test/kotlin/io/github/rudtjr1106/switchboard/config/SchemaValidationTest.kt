package io.github.rudtjr1106.switchboard.config

import kotlin.test.Test
import kotlin.test.assertTrue

class SchemaValidationTest {

    @Test
    fun `repository files pass`() {
        assertTrue(SchemaValidation.validate(Fixtures.androidConfigText, Fixtures.androidSchemaText).isEmpty())
        assertTrue(SchemaValidation.validate(Fixtures.iosConfigText, Fixtures.iosSchemaText).isEmpty())
    }

    @Test
    fun `bad date format is rejected like the workflow`() {
        val config = Fixtures.androidConfigText.replace("2026-09-17", "2026/09/17")
        val errors = SchemaValidation.validate(config, Fixtures.androidSchemaText)
        assertTrue(errors.isNotEmpty(), "expected a format error")
    }

    @Test
    fun `unknown screen is rejected`() {
        val config = Fixtures.androidConfigText.replace("\"Act\"", "\"Nowhere\"")
        assertTrue(SchemaValidation.validate(config, Fixtures.androidSchemaText).isNotEmpty())
    }

    @Test
    fun `switchboard label metadata does not change the result`() {
        val schema = SchemaRenderer.withScreens(Fixtures.androidSchemaText, Fixtures.androidSchema.catalog.screens + ScreenInfo("Settings", "설정", "MY"))
        assertTrue(schema.contains("x-switchboard"))
        val config = Fixtures.androidConfigText.replace("\"Act\"", "\"Settings\"")
        assertTrue(SchemaValidation.validate(config, schema).isEmpty())
        assertTrue(SchemaValidation.validate(config.replace("\"Settings\"", "\"Nowhere\""), schema).isNotEmpty())
    }
}
