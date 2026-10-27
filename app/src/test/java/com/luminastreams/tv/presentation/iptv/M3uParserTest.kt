package com.luminastreams.tv.presentation.iptv

import com.luminastreams.tv.data.local.iptv.ChannelEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private fun parse(m3u: String, batchSize: Int = 500): List<ChannelEntity> {
        val out = mutableListOf<ChannelEntity>()
        runBlocking {
            M3uParser.parseLines("pl_test", m3u.lineSequence(), batchSize) { batch -> out.addAll(batch) }
        }
        return out
    }

    @Test
    fun `parses a basic playlist with tvg attributes`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cnn.us" tvg-name="CNN" tvg-logo="http://logo/cnn.png" group-title="News",CNN International
            http://host/cnn.m3u8
            #EXTINF:-1 tvg-id="bbc.uk" group-title="News",BBC One HD
            http://host/bbc.m3u8
        """.trimIndent()

        val ch = parse(m3u)
        assertEquals(2, ch.size)
        assertEquals("CNN International", ch[0].name)
        assertEquals("cnn.us", ch[0].id)
        assertEquals("News", ch[0].groupTitle)
        assertEquals("http://logo/cnn.png", ch[0].logoUrl)
        assertEquals("http://host/cnn.m3u8", ch[0].streamUrl)
        assertEquals("HD", ch[1].resolution) // "BBC One HD"
    }

    @Test
    fun `strips a UTF-8 BOM on the first line`() {
        val m3u = "﻿#EXTM3U\n#EXTINF:-1,Channel A\nhttp://host/a"
        val ch = parse(m3u)
        assertEquals(1, ch.size)
        assertEquals("Channel A", ch[0].name)
    }

    @Test
    fun `adult groups are filtered out`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="XXX Adult",Blocked Channel
            http://host/blocked
            #EXTINF:-1 group-title="Kids",Cartoon Time
            http://host/kids
        """.trimIndent()

        val ch = parse(m3u)
        assertEquals(1, ch.size)
        assertEquals("Cartoon Time", ch[0].name)
    }

    @Test
    fun `EXTGRP sets the group when group-title is absent`() {
        val m3u = """
            #EXTM3U
            #EXTGRP:Sports
            #EXTINF:-1,Match Channel
            http://host/match
        """.trimIndent()

        val ch = parse(m3u)
        assertEquals("Sports", ch[0].groupTitle)
    }

    @Test
    fun `falls back to a generated id when tvg-id is missing`() {
        val ch = parse("#EXTM3U\n#EXTINF:-1,No Id Channel\nhttp://host/x")
        assertTrue(ch[0].id.isNotBlank())
        assertTrue(ch[0].id.endsWith("_1"))
    }

    @Test
    fun `batches are emitted at batchSize`() {
        val sb = StringBuilder("#EXTM3U\n")
        repeat(5) { i -> sb.append("#EXTINF:-1,Ch $i\nhttp://host/$i\n") }
        val batches = mutableListOf<Int>()
        runBlocking {
            M3uParser.parseLines("pl", sb.toString().lineSequence(), batchSize = 2) { batches.add(it.size) }
        }
        // 5 channels, batchSize 2 -> [2, 2, 1]
        assertEquals(listOf(2, 2, 1), batches)
    }
}
