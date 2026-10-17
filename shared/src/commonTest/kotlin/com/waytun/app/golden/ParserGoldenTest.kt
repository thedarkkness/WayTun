package com.waytun.app.golden

import com.waytun.app.core.obfuscation.AwgConfigObfuscator
import com.waytun.app.core.obfuscation.ObfuscationGenerator
import com.waytun.app.core.obfuscation.ObfuscationProfile
import com.waytun.app.core.obfuscation.ObfuscationSettings
import com.waytun.app.core.parser.JsonSubscription
import com.waytun.app.core.parser.ShareLinkList
import com.waytun.app.core.parser.SingBoxLinkParser
import com.waytun.app.core.parser.TunnelDescriptor
import com.waytun.app.core.text.FlagName
import com.waytun.app.vpn.xray.XrayConfigBuilder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The shared implementation must reproduce what 1.3.2 produced on Android, byte for byte: stored links and JSON
 * entries are compared as text on every subscription refresh, and Xray gets exactly the same configs.
 */
class ParserGoldenTest {

    private fun xray(block: () -> String): String = try {
        block()
    } catch (e: Exception) {
        "ERR:${e::class.simpleName}:${e.message}"
    }

    @Test
    fun shareLinks() {
        for (g in GoldenData.links) {
            assertEquals(g.canParse, SingBoxLinkParser.canParse(g.input), "canParse ${g.input}")
            val parsed = SingBoxLinkParser.parse(g.input)
            assertEquals(g.parsed, parsed?.toString(), "parse ${g.input}")
            assertEquals(g.describe, parsed?.let { TunnelDescriptor.describe(it) }, "describe ${g.input}")
            assertEquals(g.xrayPlain, parsed?.let { xray { XrayConfigBuilder.build(it, null, false, null) } }, "xray ${g.input}")
            assertEquals(
                g.xrayObfuscated,
                parsed?.let { xray { XrayConfigBuilder.build(it, GoldenInputs.profile, true, GoldenInputs.XRAY_LOG) } },
                "xray obfuscated ${g.input}"
            )
        }
    }

    @Test
    fun subscriptions() {
        for (g in GoldenData.subscriptions) {
            val label = g.text.take(60)
            assertEquals(g.extract, ShareLinkList.extract(g.text), "extract $label")
            assertEquals(g.countAll, ShareLinkList.countAllLinks(g.text), "countAll $label")
            assertEquals(g.looksLikeJson, JsonSubscription.looksLikeJson(g.text), "looksLikeJson $label")
            assertEquals(g.countEntries, JsonSubscription.countEntries(g.text), "countEntries $label")
            assertEquals(g.entries, JsonSubscription.entries(g.text), "entries $label")
            assertEquals(g.toLinks, JsonSubscription.toLinks(g.text), "toLinks $label")
        }
    }

    @Test
    fun jsonEntriesRunAsXrayConfigs() {
        for (g in GoldenData.jsonEntries) {
            val label = g.entry.take(60)
            assertEquals(g.linkOf, JsonSubscription.linkOf(g.entry), "linkOf $label")
            assertEquals(g.host, JsonSubscription.linkOf(g.entry)?.let { SingBoxLinkParser.parse(it)?.server }, "host $label")
            assertEquals(g.plain, xray { XrayConfigBuilder.fromJson(g.entry, null, null, false, null) }, "fromJson $label")
            assertEquals(
                g.pinned,
                xray { XrayConfigBuilder.fromJson(g.entry, GoldenInputs.PINNED_IP, g.host, true, GoldenInputs.PINNED_LOG) },
                "fromJson pinned $label"
            )
        }
    }

    @Test
    fun obfuscationSettingsAndProfilesKeepTheirStoredForm() {
        for ((json, text) in GoldenData.settingsJson) {
            val settings = ObfuscationSettings.fromJson(json)
            assertEquals(text, settings.toString())
            assertEquals(json, settings.toJson())
        }
        for (g in GoldenData.profiles) {
            val profile = ObfuscationGenerator.generate(GoldenInputs.settings[g.settingsIndex], Random(g.seed), g.now)
            assertEquals(g.profile, profile.toString())
            assertEquals(g.json, profile.toJson())
            assertEquals(profile, ObfuscationProfile.fromJson(g.json))
        }
        assertEquals(GoldenData.profileJson, GoldenInputs.profile.toJson())
        for ((conf, expected) in GoldenData.awg) assertEquals(expected, AwgConfigObfuscator.apply(conf, GoldenInputs.profile))
    }

    @Test
    fun flagNames() {
        for ((name, expected) in GoldenData.flagNames) assertEquals(expected, FlagName.parse(name).toString(), name)
    }
}
