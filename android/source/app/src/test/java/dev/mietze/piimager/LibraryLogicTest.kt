package dev.mietze.piimager

import dev.mietze.piimager.data.OsItem
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

class LibraryLogicTest {
 private fun image(name: String = "Test image", url: String? = "https://example.org/pi.img.xz", tags: List<String> = listOf("pi5-64bit"), children: List<OsItem> = emptyList()): OsItem =
  OsItem(name, "Description", null, url, 1048576, "a".repeat(64), 524288, "b".repeat(64), "2026-10-13", "none", tags, children)
 private fun invalid(block: () -> Unit) {
  try { block(); fail("Expected validation to reject the input") } catch (_: IllegalArgumentException) { }
 }
 @Test fun exactlyTenPreparedSources() { assertEquals(10, LibraryDefaults.entries.size) }
 @Test fun preparedSourceIdsAreUnique() { assertEquals(10, LibraryDefaults.ids.size) }
 @Test fun preparedSourcesContainNoImagePayloadsOrDirectImages() {
  assertTrue(LibraryDefaults.entries.all { it.imageUrl == null && it.imageSha256 == null && it.match.isNotBlank() })
 }
 @Test fun firstActivationEnablesExactlyThePreparedTen() {
  assertEquals(LibraryDefaults.ids, LibraryActivation.enabledSources(true, true, emptySet()))
 }
 @Test fun reenablingPreservesIndividualChoices() {
  val ids = setOf("ubuntu", "dietpi")
  assertEquals(ids, LibraryActivation.enabledSources(true, false, ids))
 }
 @Test fun disablingPreservesChoicesForNextTime() {
  val ids = setOf("libreelec")
  assertEquals(ids, LibraryActivation.enabledSources(false, false, ids))
 }
 @Test fun disablingFreshLibraryDoesNotEnableEntries() {
  assertTrue(LibraryActivation.enabledSources(false, true, emptySet()).isEmpty())
 }
 @Test fun enabledEmptySelectionIsNotSilentlyReset() {
  assertTrue(LibraryActivation.enabledSources(true, false, emptySet()).isEmpty())
 }
 @Test fun supportedDiskImagesAcceptExpectedSuffixes() {
  listOf("img", "raw", "img.xz", "img.gz", "img.zip", "zip").forEach { assertTrue(it, LibraryWire.supportedImage("https://example.org/pi.$it")) }
 }
 @Test fun unrelatedPayloadsAreRejected() {
  listOf("exe", "apk", "html", "pdf", "zst").forEach { assertFalse(it, LibraryWire.supportedImage("https://example.org/pi.$it")) }
 }
 @Test fun ordinaryHttpsImageUrlIsValid() { assertTrue(LibraryWire.validUrl("https://example.org/pi.img.xz", true)) }
 @Test fun customLinksRequireHttps() { assertFalse(LibraryWire.validUrl("http://example.org/pi.img", true)) }
 @Test fun legacyCatalogueHttpDoesNotBecomeHttpsByAssumption() { assertTrue(LibraryWire.validUrl("http://example.org/pi.img", false)) }
 @Test fun embeddedCredentialsAreRejected() { assertFalse(LibraryWire.validUrl("https://user:secret@example.org/pi.img", true)) }
 @Test fun localSchemesAndMissingHostsAreRejected() {
  listOf("file:///data/pi.img", "content://local/pi.img", "javascript:alert(1)", "https:///pi.img").forEach { assertFalse(it, LibraryWire.validUrl(it)) }
 }
 @Test fun controlCharactersAndFragmentsAreRejected() {
  assertFalse(LibraryWire.validUrl("https://example.org/a\n.img"))
  assertFalse(LibraryWire.validUrl("https://example.org/a.img#fragment"))
 }
 @Test fun boardFilterDoesNotInferSupportFromMissingTags() { assertFalse(LibraryFilters.matchesBoard(emptyList(), "Pi 5")) }
 @Test fun allModelsKeepsUnspecifiedImagesVisible() { assertTrue(LibraryFilters.matchesBoard(emptyList(), "All models")) }
 @Test fun boardFilterSeparatesPiFourAndFive() {
  assertTrue(LibraryFilters.matchesBoard(listOf("pi5-64bit"), "Pi 5"))
  assertFalse(LibraryFilters.matchesBoard(listOf("pi4-64bit"), "Pi 5"))
 }
 @Test fun zeroTwoUsesPublisherPiThreeTags() { assertTrue(LibraryFilters.matchesBoard(listOf("pi3-64bit"), "Zero 2")) }
 @Test fun categoryMatchingIncludesNestedImagesOnly() {
  val child = image(name = "A supported variant")
  val other = image(name = "Unrelated OS", url = "https://example.org/other.img")
  val parent = image(name = "DietPi", url = null, children = listOf(child))
  assertEquals(listOf(child), LibraryWire.matchingVariants(listOf(parent, other), "DietPi"))
 }
 @Test fun duplicateImageUrlsAreCollapsed() {
  val item = image("Ubuntu Test")
  assertEquals(1, LibraryWire.matchingVariants(listOf(item, item), "Ubuntu").size)
 }
 @Test fun imageHandoffPreservesValidationMetadata() {
  val item = image()
  assertEquals(item, LibraryWire.decode(LibraryWire.encode(item)))
 }
 @Test fun imageHandoffRejectsInvalidHash() {
  val json = JSONObject(LibraryWire.encode(image())).put("extract_sha256", "not-a-hash")
  invalid { LibraryWire.decode(json.toString()) }
 }
 @Test fun customIdIsStableForTheSameLink() {
  val one = LibraryBackup.customEntry("One", "https://example.org/pi.img", "")
  val two = LibraryBackup.customEntry("Renamed", "https://example.org/pi.img", "")
  assertEquals(one.id, two.id)
 }
 @Test fun customLinkHashIsNormalizedAndOptional() {
  val item = LibraryBackup.customEntry("Pi", "https://example.org/pi.img.xz", "A".repeat(64))
  assertEquals("a".repeat(64), item.imageSha256)
  assertNull(LibraryBackup.customEntry("Pi", "https://example.org/pi.img", "").imageSha256)
 }
 @Test fun invalidCustomNameAndHashAreRejected() {
  invalid { LibraryBackup.customEntry("", "https://example.org/pi.img", "") }
  invalid { LibraryBackup.customEntry("Pi", "https://example.org/pi.img", "1234") }
 }
 @Test fun backupRoundTripKeepsCustomSourceFlagsAndFavorites() {
  val own = LibraryBackup.customEntry("My Pi image", "https://example.org/my.img.xz", "c".repeat(64))
  val state = LibraryState(enabled = true, entries = LibraryDefaults.entries + own, enabledIds = setOf("ubuntu", own.id), favorites = setOf(own.id))
  val plan = LibraryBackup.parse(LibraryBackup.encode(state))
  assertEquals(listOf(own), plan.entries)
  assertEquals(state.enabledIds, plan.enabledIds)
  assertEquals(state.favorites, plan.favorites)
 }
 @Test fun backupDoesNotContainDevicesPasswordsOrActivationFlag() {
  val json = JSONObject(LibraryBackup.encode(LibraryState()))
  listOf("password", "devices", "ssh", "enabled", "images").forEach { assertFalse(it, json.has(it)) }
 }
 @Test fun backupRejectsUnknownFormatAndSchema() {
  invalid { LibraryBackup.parse("{\"format\":\"something-else\",\"schema\":1}") }
  invalid { LibraryBackup.parse("{\"format\":\"raspberry-manager-library\",\"schema\":9}") }
 }
 @Test fun backupRejectsOversizedInputBeforeParsing() {
  invalid { LibraryBackup.parse("x".repeat(LibraryBackup.MAX_BYTES + 1)) }
  invalid { LibraryBackup.readLimited(ByteArrayInputStream(ByteArray(LibraryBackup.MAX_BYTES + 1))) }
 }
 @Test fun backupRejectsTooManySources() {
  val sources = JSONArray()
  val item = JSONObject().put("name", "My Pi").put("image_url", "https://example.org/pi.img")
  repeat(101) { sources.put(item) }
  invalid { LibraryBackup.parseSources(sources) }
 }
 @Test fun backupDropsUnknownIds() {
  val json = JSONObject(LibraryBackup.encode(LibraryState())).put("enabled_ids", JSONArray(listOf("ubuntu", "not-a-source")))
  assertEquals(setOf("ubuntu"), LibraryBackup.parse(json.toString()).enabledIds)
 }
 @Test fun backupRejectsCredentialBearingImportedUrl() {
  val json = JSONObject(LibraryBackup.encode(LibraryState())).put("custom_sources", JSONArray().put(JSONObject().put("name", "Private").put("image_url", "https://alice:secret@example.org/pi.img")))
  invalid { LibraryBackup.parse(json.toString()) }
 }
}
