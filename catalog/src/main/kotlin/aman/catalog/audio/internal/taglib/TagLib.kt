package aman.catalog.audio.internal.taglib

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import aman.catalog.audio.models.ExtendedMetadata
import aman.catalog.audio.models.TrackPicture
import aman.taglib.TagLib as NativeTagLib
import java.io.File
import java.io.IOException

import aman.catalog.audio.Catalog
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

object TagLibHelper {

    private val YEAR_REGEX = Regex("\\b\\d{4}\\b")

    suspend fun extract(path: String): ExtendedMetadata = withContext(Dispatchers.IO) {
        Catalog.getMutexFor(path).withLock {
        try {
            val rawMap = NativeTagLib.getMetadata(path) ?: return@withContext ExtendedMetadata.EMPTY

            var rating = normalizeRating(rawMap["CONTENT_RATING"] ?: rawMap["CONTENTRATING"])
            var bitrate = rawMap["BITRATE"]?.toIntOrNull() ?: 0
            var sampleRate = rawMap["SAMPLERATE"]?.toIntOrNull() ?: 0
            var channels = rawMap["CHANNELS"]?.toIntOrNull() ?: 0
            var bits = rawMap["BITS_PER_SAMPLE"]?.toIntOrNull() ?: 0
            var rawFormat = rawMap["FORMAT"] ?: ""
            var durationMs = rawMap["DURATION_SEC"]?.toLongOrNull()?.times(1000L) ?: 0L

            // 1. Direct MP4 DASH/fragmented header parser
            if (durationMs == 0L) {
                try {
                    val dashDur = parseMp4DashDuration(path)
                    if (dashDur > 0L) {
                        durationMs = dashDur
                    }
                } catch (e: Exception) {
                    Log.w("TagLibHelper", "DASH duration parse failed for $path", e)
                }
            }

            // 2. Fall back to Android's MediaExtractor if TagLib couldn't determine
            // bitrate, sample rate, channel count, or duration.
            if (bitrate == 0 || channels == 0 || sampleRate == 0 || durationMs == 0L) {
                try {
                    val androidStats = getAndroidAudioStats(path)
                    if (androidStats.isValid) {
                        if (bitrate == 0) bitrate = androidStats.bitrate
                        if (sampleRate == 0) sampleRate = androidStats.sampleRate
                        if (channels == 0) channels = androidStats.channels
                        if (rawFormat.isBlank()) rawFormat = androidStats.prettyFormat
                        if (durationMs == 0L) durationMs = androidStats.durationMs
                    }
                } catch (e: Exception) {
                    Log.w("TagLibHelper", "Android Extractor failed for $path", e)
                }
            }

            // 3. Fall back to MediaMetadataRetriever if duration is still 0
            if (durationMs == 0L) {
                try {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)
                        val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val parsedDur = durStr?.toLongOrNull() ?: 0L
                        if (parsedDur > 0L) {
                            durationMs = parsedDur
                        }
                    } finally {
                        retriever.release()
                    }
                } catch (e: Exception) {
                    Log.w("TagLibHelper", "MediaMetadataRetriever failed for $path", e)
                }
            }

            // 4. Last resort: estimate bitrate from file size / duration.
            if (bitrate == 0) {
                bitrate = calculateBitrate(path, durationMs)
            }

            val composer = rawMap["COMPOSER"] ?: ""
            // Lyricist precedence: LYRICIST -> SONGWRITER -> WRITER -> TEXT -> AUTHOR
            val lyricist = rawMap["LYRICIST"] ?: rawMap["SONGWRITER"] ?: rawMap["WRITER"] ?: rawMap["TEXT"] ?: rawMap["AUTHOR"] ?: ""

            val albumArtist = rawMap["ALBUMARTIST"] ?: rawMap["ALBUM ARTIST"] ?: rawMap["ALBUM_ARTIST"] ?: rawMap["BAND"] ?: ""
            val dateStr = rawMap["DATE"] ?: rawMap["YEAR"] ?: rawMap["ORIGINALDATE"] ?: rawMap["ORIGINALYEAR"] ?: ""

            // Extract the first 4-digit year found anywhere in the date string —
            // handles formats like "2023", "2023-05-20", "23/05/2023", etc.
            val parsedYear = YEAR_REGEX.find(dateStr)?.value?.toIntOrNull() ?: 0

            val releaseDateStr = rawMap["RELEASEDATE"] ?: rawMap["RELEASE DATE"] ?: rawMap["ORIGINALRELEASEDATE"] ?: ""
            val trackStr = rawMap["TRACKNUMBER"] ?: rawMap["TRACK"] ?: rawMap["TRACKNUM"] ?: ""
            val discStr = rawMap["DISCNUMBER"] ?: rawMap["DISC"] ?: rawMap["DISCNUM"] ?: ""

            val title = rawMap["TITLE"] ?: ""
            val artist = rawMap["ARTIST"] ?: ""
            val album = rawMap["ALBUM"] ?: ""
            val genre = rawMap["GENRE"] ?: ""

            val trackNum = trackStr.substringBefore('/').trim().toIntOrNull() ?: 0
            val discNum = discStr.substringBefore('/').trim().toIntOrNull() ?: 0

            val replayGainTrackGain = (rawMap["REPLAYGAIN_TRACK_GAIN"] ?: rawMap["REPLAYGAIN_TRACK_GAIN_DB"])?.replace(" dB", "", ignoreCase = true)?.trim()?.toDoubleOrNull() ?: 0.0
            val replayGainTrackPeak = (rawMap["REPLAYGAIN_TRACK_PEAK"])?.toDoubleOrNull() ?: 0.0
            val replayGainAlbumGain = (rawMap["REPLAYGAIN_ALBUM_GAIN"] ?: rawMap["REPLAYGAIN_ALBUM_GAIN_DB"])?.replace(" dB", "", ignoreCase = true)?.trim()?.toDoubleOrNull() ?: 0.0
            val replayGainAlbumPeak = (rawMap["REPLAYGAIN_ALBUM_PEAK"])?.toDoubleOrNull() ?: 0.0

            val hasLyrics = !rawMap["LYRICS"].isNullOrBlank() || 
                            !rawMap["USLT"].isNullOrBlank() || 
                            !rawMap["UNSYNCEDLYRICS"].isNullOrBlank() || 
                            !rawMap["UNSYNCED LYRICS"].isNullOrBlank() || 
                            !rawMap["©LYR"].isNullOrBlank()

            return@withContext ExtendedMetadata(
                contentRating = rating,
                bitrate = bitrate,
                sampleRate = sampleRate,
                channels = channels,
                codec = rawFormat,
                bitsPerSample = bits,
                replayGainTrackGain = replayGainTrackGain,
                replayGainTrackPeak = replayGainTrackPeak,
                replayGainAlbumGain = replayGainAlbumGain,
                replayGainAlbumPeak = replayGainAlbumPeak,
                foundYear = parsedYear,
                foundReleaseDate = releaseDateStr,
                foundComposer = composer,
                foundLyricist = lyricist,
                foundAlbumArtist = albumArtist,
                foundTrackNumber = trackNum,
                foundDiscNumber = discNum,
                foundTitle = title,
                foundArtist = artist,
                foundAlbum = album,
                foundGenre = genre,
                foundDuration = durationMs,
                hasLyrics = hasLyrics
            )

        } catch (e: Exception) {
            Log.e("TagLibDebug", "Failed to extract metadata from: $path", e)
            return@withContext ExtendedMetadata.EMPTY
        }
        }
    }

    private fun normalizeRating(value: String?): Int {
        if (value.isNullOrBlank()) return 0
        
        return when (value.trim().lowercase()) {
            // Standard iTunes integer flags (passed as strings)
            "1", "4" -> 1 // Explicit
            "2" -> 2      // Clean
            
            // Text-based tags (Vorbis, custom ID3)
            "explicit", "e" -> 1
            "clean", "c" -> 2
            
            else -> 0
        }
    }

    /**
     * Calculates approximate bitrate from file size and duration.
     *
     * LIMITATION:
     * Includes embedded artwork, ID3 tags, and container overhead.
     *
     * Example:
     * A 320kbps MP3 with 3MB artwork may report 600+ kbps.
     *
     * Used ONLY as last-resort fallback.
     */
    private fun calculateBitrate(path: String, knownDurationMs: Long = 0L): Int {
        try {
            val file = File(path)
            if (!file.exists()) return 0

            val sizeInBits = file.length() * 8
            if (knownDurationMs > 0L) {
                val durationSec = knownDurationMs / 1000.0
                return (sizeInBits / durationSec / 1000).toInt()
            }

            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(path)
                var durationUs: Long = 0
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        if (format.containsKey(MediaFormat.KEY_DURATION)) {
                            durationUs = format.getLong(MediaFormat.KEY_DURATION)
                            break
                        }
                    }
                }

                if (durationUs > 0) {
                    val durationSec = durationUs / 1_000_000.0
                    return (sizeInBits / durationSec / 1000).toInt()
                }
            } finally {
                extractor.release()
            }
        } catch (_: Exception) {
        }
        return 0
    }

    private data class AndroidStats(
        val bitrate: Int,
        val sampleRate: Int,
        val channels: Int,
        val prettyFormat: String,
        val isValid: Boolean,
        val durationMs: Long = 0L
    )

    private fun getAndroidAudioStats(path: String): AndroidStats {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("audio/")) {
                    val sr = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                        format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0

                    val ch = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

                    val br = if (format.containsKey(MediaFormat.KEY_BIT_RATE))
                        format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000 else 0

                    val durMs = if (format.containsKey(MediaFormat.KEY_DURATION))
                        format.getLong(MediaFormat.KEY_DURATION) / 1000L else 0L

                    val prettyCodec = when (mime) {
                        "audio/ac3" -> "Dolby Digital"
                        "audio/eac3" -> "Dolby Digital+"
                        "audio/mp4a-latm" -> "AAC"
                        "audio/flac" -> "FLAC"
                        "audio/opus" -> "Opus"
                        else -> mime.substringAfter("/")
                    }

                    return AndroidStats(br, sr, ch, prettyCodec, true, durMs)
                }
            }
        } catch (_: IOException) {
        } finally {
            extractor.release()
        }
        return AndroidStats(0, 0, 0, "", false, 0L)
    }

    // Lyrics extraction
    suspend fun extractLyrics(path: String): String {
        Catalog.getMutexFor(path).withLock {
        try {
            val rawMap = NativeTagLib.getMetadata(path) ?: return ""
            if (rawMap.isEmpty()) return ""

            var lyrics = rawMap["LYRICS"]
            if (lyrics.isNullOrBlank()) lyrics = rawMap["USLT"]
            if (lyrics.isNullOrBlank()) lyrics = rawMap["UNSYNCEDLYRICS"]
            if (lyrics.isNullOrBlank()) lyrics = rawMap["UNSYNCED LYRICS"]
            if (lyrics.isNullOrBlank()) lyrics = rawMap["©LYR"]

            return lyrics ?: ""
        } catch (e: Exception) {
            Log.e("TagLibHelper", "Failed to extract lyrics for $path", e)
            return ""
        }
        }
    }

    // Artwork extraction
    suspend fun extractPictures(path: String): List<TrackPicture> {
        Catalog.getMutexFor(path).withLock {
        try {
            val artworks = NativeTagLib.getArtwork(path) ?: return emptyList()
            return artworks.map { art ->
                TrackPicture(
                    data = art.data,
                    mimeType = art.mimeType ?: "image/*",
                    description = art.description ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e("TagLibHelper", "Failed to extract artwork for $path", e)
            return emptyList()
        }
        }
    }

    /**
     * Extracts duration from fragmented MP4/DASH containers (e.g. FLAC-in-MP4, DASH streams)
     * by parsing the `mvex` -> `mehd` (Movie Extends Header) and `mdhd`/`mvhd` timescale atoms.
     */
    private fun parseMp4DashDuration(path: String): Long {
        val file = File(path)
        if (!file.exists() || file.length() < 32) return 0L

        // Read up to first 1 MB which contains the 'moov' atom and embedded tags/artwork
        val bufferSize = minOf(file.length(), 1024 * 1024L).toInt()
        val data = ByteArray(bufferSize)
        java.io.FileInputStream(file).use { input ->
            var bytesRead = 0
            while (bytesRead < bufferSize) {
                val read = input.read(data, bytesRead, bufferSize - bytesRead)
                if (read == -1) break
                bytesRead += read
            }
        }

        // 1. Find timescale from mdhd or mvhd
        var timescale = 0L
        val mdhdIdx = findSubsequence(data, ATOM_MDHD)
        if (mdhdIdx != -1 && mdhdIdx + 24 <= data.size) {
            val version = data[mdhdIdx + 4].toInt()
            timescale = if (version == 1) readUint32(data, mdhdIdx + 24) else readUint32(data, mdhdIdx + 16)
        }

        if (timescale <= 0L) {
            val mvhdIdx = findSubsequence(data, ATOM_MVHD)
            if (mvhdIdx != -1 && mvhdIdx + 24 <= data.size) {
                val version = data[mvhdIdx + 4].toInt()
                timescale = if (version == 1) readUint32(data, mvhdIdx + 24) else readUint32(data, mvhdIdx + 16)
            }
        }

        if (timescale <= 0L) return 0L

        // 2. Find fragment duration from mehd (Movie Extends Header)
        val mehdIdx = findSubsequence(data, ATOM_MEHD)
        if (mehdIdx != -1 && mehdIdx + 12 <= data.size) {
            val version = data[mehdIdx + 4].toInt()
            val fragmentDuration = if (version == 1) readUint64(data, mehdIdx + 8) else readUint32(data, mehdIdx + 8)
            if (fragmentDuration > 0L) {
                return (fragmentDuration * 1000L) / timescale
            }
        }

        return 0L
    }

    private val ATOM_MDHD = byteArrayOf('m'.code.toByte(), 'd'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte())
    private val ATOM_MVHD = byteArrayOf('m'.code.toByte(), 'v'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte())
    private val ATOM_MEHD = byteArrayOf('m'.code.toByte(), 'e'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte())

    private fun findSubsequence(data: ByteArray, pattern: ByteArray): Int {
        if (pattern.isEmpty() || pattern.size > data.size) return -1
        for (i in 0..(data.size - pattern.size)) {
            var found = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    private fun readUint32(data: ByteArray, offset: Int): Long {
        if (offset + 4 > data.size) return 0L
        return ((data[offset].toLong() and 0xFF) shl 24) or
               ((data[offset + 1].toLong() and 0xFF) shl 16) or
               ((data[offset + 2].toLong() and 0xFF) shl 8) or
               (data[offset + 3].toLong() and 0xFF)
    }

    private fun readUint64(data: ByteArray, offset: Int): Long {
        if (offset + 8 > data.size) return 0L
        var res = 0L
        for (i in 0 until 8) {
            res = (res shl 8) or (data[offset + i].toLong() and 0xFF)
        }
        return res
    }
}
