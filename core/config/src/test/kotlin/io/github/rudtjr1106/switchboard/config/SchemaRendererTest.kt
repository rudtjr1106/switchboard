package io.github.rudtjr1106.switchboard.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchemaRendererTest {

    @Test
    fun `rewriting the same screens keeps the android schema byte for byte`() {
        val text = Fixtures.androidSchemaText
        val screens = Fixtures.androidSchema.catalog.screens
        assertEquals(text, SchemaRenderer.withScreens(text, screens))
    }

    @Test
    fun `rewriting the same screens keeps the ios schema byte for byte`() {
        val text = Fixtures.iosSchemaText
        assertEquals(text, SchemaRenderer.withScreens(text, Fixtures.iosSchema.catalog.screens))
    }

    @Test
    fun `adding screens with labels updates enum and metadata`() {
        val screens = Fixtures.androidSchema.catalog.screens + ScreenInfo("Settings", "설정", "MY")
        val updated = SchemaRenderer.withScreens(Fixtures.androidSchemaText, screens)
        val schema = ConfigSchema.parse(updated)
        assertEquals(36, schema.screenIds.size)
        assertEquals("설정", schema.catalog.label("Settings"))
        assertTrue(updated.contains("\"x-switchboard\""))
        assertTrue(updated.contains("\"Settings\": { \"label\": \"설정\", \"group\": \"MY\" }"))
    }

    @Test
    fun `rendered schema parses and validates the initial config`() {
        val text = SchemaRenderer.render(ScreenCatalog.starter().screens)
        val schema = ConfigSchema.parse(text)
        assertTrue(schema.supportsMinimumVersion)
        assertEquals(listOf("ALL"), schema.screenIds)
        assertFalse(text.contains("x-switchboard").not() && schema.catalog.hasMetadata)
        val config = io.github.rudtjr1106.switchboard.config.templates.RepoTemplates.initialConfig(schema)
        assertTrue(SchemaValidation.validate(config, text).isEmpty())
    }
}
