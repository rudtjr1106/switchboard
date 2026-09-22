package io.github.rudtjr1106.switchboard.config

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigDiffTest {

    private val today = LocalDate.of(2026, 9, 22)
    private val original = ConfigCodec.decode(Fixtures.iosConfigText, Fixtures.iosSchema)
    private val maintenance = original.notices.single()

    @Test
    fun `no changes for identical content`() {
        assertTrue(ConfigDiff.between(original, original).isEmpty())
        assertTrue(original.contentEquals(original.copy()))
    }

    @Test
    fun `enabling a notice is reported once`() {
        val draft = original.update(maintenance.id) { it.copy(enabled = true) }
        val changes = ConfigDiff.between(original, draft)
        assertEquals(listOf(ChangeKind.ENABLED), changes.map { it.kind })
        assertEquals("원격 설정: 안내 켜짐 · 서비스 점검 중이에요", ConfigDiff.commitTitle(changes))
    }

    @Test
    fun `enable plus edit is reported as two changes`() {
        val draft = original.update(maintenance.id) { it.copy(enabled = true, until = "2026-09-30") }
        val kinds = ConfigDiff.between(original, draft).map { it.kind }
        assertEquals(listOf(ChangeKind.ENABLED, ChangeKind.MODIFIED), kinds)
    }

    @Test
    fun `add remove and minimum version`() {
        val (added, _) = original.duplicate(maintenance.id)!!
        val draft = added.remove(maintenance.id).copy(minimumVersion = "2.3.0")
        val changes = ConfigDiff.between(original, draft)
        assertEquals(listOf(ChangeKind.MODIFIED, ChangeKind.REMOVED, ChangeKind.ADDED), changes.map { it.kind })
        assertEquals("최소 버전 없음 → 2.3.0", changes[0].text)
        assertEquals("원격 설정: 최소 버전 없음 → 2.3.0 외 2건", ConfigDiff.commitTitle(changes))
    }

    @Test
    fun `turning on the kill switch is dangerous`() {
        assertFalse(ConfigDiff.isDangerous(original, original, today))
        val draft = original.update(maintenance.id) { it.copy(enabled = true) }
        assertTrue(ConfigDiff.isDangerous(original, draft, today))
        val expired = original.update(maintenance.id) { it.copy(enabled = true, until = "2026-09-01") }
        assertFalse(ConfigDiff.isDangerous(original, expired, today))
    }

    @Test
    fun `status follows enabled and until`() {
        assertEquals(NoticeStatus.OFF, maintenance.status(today))
        assertEquals(NoticeStatus.LIVE, maintenance.copy(enabled = true).status(today))
        assertEquals(NoticeStatus.LIVE, maintenance.copy(enabled = true, until = "2026-09-22").status(today))
        assertEquals(NoticeStatus.EXPIRED, maintenance.copy(enabled = true, until = "2026-09-21").status(today))
    }
}
