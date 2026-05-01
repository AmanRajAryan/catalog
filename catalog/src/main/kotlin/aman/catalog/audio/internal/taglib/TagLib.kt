package aman.catalog.audio.internal.taglib

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import aman.catalog.audio.models.ExtendedMetadata
import aman.catalog.audio.models.TrackPicture
import aman.taglib.TagLib as NativeTagLib
import java.io.File
import java.io.IOException

object TagLibHelper {

    fun extract(path: String): ExtendedMetadata {
        try {
            val rawMap = NativeTagLib.getMetadata(path) ?: return ExtendedMetadata.EMPTY

            var rating = normalizeRating(rawMap["CONTENT_RATING"] ?: rawMap["CONTENTRATING"])
            var bitrate = rawMap["BITRATE"]?.toIntOrNull() ?: 0
            var sampleRate = rawMap["SAMPLERATE"]?.toIntOrNull() ?: 0
            var channels = rawMap["CHANNELS"]?.toIntOrNull() ?: 0
            var bits = rawMap["BITS_PER_SAMPLE"]?.toIntOrNull() ?: 0
            var rawFormat = rawMap["FORMAT"] ?: ""

            // Fall back to Android's MediaExtractor if TagLib couldn't determine bitrate,
            // sample rate, or channel count.
            if (bitrate == 0 || channels == 0 || sampleRate == 0) {
                try {
                    val androidStats = getAndroidAudioStats(path)
                    if (androidStats.isValid) {
                        if (bitrate == 0) bitrate = androidStats.bitrate
                        if (sampleRate == 0) sampleRate = androidStats.sampleRate
                        if (channels == 0) channels = androidStats.channels
                        if (rawFormat.isBlank()) rawFormat = androidStats.prettyFormat
                    }
                } catch (e: Exception) {
                    Log.w("TagLibHelper", "Android Extractor failed for $path", e)
                }
            }

            // Last resort: estimate bitrate from file size / duration.
            // Inaccurate for files with large embedded artwork, but better than returning 0.
            if (bitrate == 0) {
                bitrate = calculateBitrate(path)
            }


            val composer = rawMap["COMPOSER"] ?: ""
            // Lyricist precedence: LYRICIST -> SONGWRITER -> WRITER
            val lyricist = rawMap["LYRICIST"] ?: rawMap["SONGWRITER"] ?: rawMap["WRITER"] ?: ""

            val albumArtist = rawMap["ALBUMARTIST"] ?: ""
            val dateStr = rawMap["DATE"] ?: ""

            // Extract the first 4-digit year found anywhere in the date string —
            // handles formats like "2023", "2023-05-20", "23/05/2023", etc.
            val yearPattern = Regex("\\b\\d{4}\\b")
            val parsedYear = yearPattern.find(dateStr)?.value?.toIntOrNull() ?: 0

            val releaseDateStr = rawMap["RELEASEDATE"] ?: ""
            val trackStr = rawMap["TRACKNUMBER"] ?: rawMap["TRACK"] ?: ""
            val discStr = rawMap["DISCNUMBER"] ?: rawMap["DISC"] ?: ""

            val title = rawMap["TITLE"] ?: ""
            val artist = rawMap["ARTIST"] ?: ""
            val album = rawMap["ALBUM"] ?: ""
            val genre = rawMap["GENRE"] ?: ""

            val trackNum = trackStr.substringBefore('/').toIntOrNull() ?: 0
            val discNum = discStr.substringBefore('/').toIntOrNull() ?: 0

            val replayGainTrackGain = (rawMap["REPLAYGAIN_TRACK_GAIN"] ?: rawMap["replaygain_track_gain"])?.replace(" dB", "", ignoreCase = true)?.trim()?.toDoubleOrNull() ?: 0.0
            val replayGainTrackPeak = (rawMap["REPLAYGAIN_TRACK_PEAK"] ?: rawMap["replaygain_track_peak"])?.toDoubleOrNull() ?: 0.0
            val replayGainAlbumGain = (rawMap["REPLAYGAIN_ALBUM_GAIN"] ?: rawMap["replaygain_album_gain"])?.replace(" dB", "", ignoreCase = true)?.trim()?.toDoubleOrNull() ?: 0.0
            val replayGainAlbumPeak = (rawMap["REPLAYGAIN_ALBUM_PEAK"] ?: rawMap["replaygain_album_peak"])?.toDoubleOrNull() ?: 0.0

            return ExtendedMetadata(
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
                foundGenre = genre
            )

        } catch (e: Exception) {
            Log.e("TagLibDebug", "Failed to extract metadata from: $path", e)
            return ExtendedMetadata.EMPTY
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
    private fun calculateBitrate(path: String): Int {
        try {
            val file = File(path)
            if (!file.exists()) return 0

            val sizeInBits = file.length() * 8
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
        val isValid: Boolean
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

                    val prettyCodec = when (mime) {
                        "audio/ac3" -> "Dolby Digital"
                        "audio/eac3" -> "Dolby Digital+"
                        "audio/mp4a-latm" -> "AAC"
                        "audio/flac" -> "FLAC"
                        "audio/opus" -> "Opus"
                        else -> mime.substringAfter("/")
                    }

                    return AndroidStats(br, sr, ch, prettyCodec, true)
                }
            }
        } catch (_: IOException) {
        } finally {
            extractor.release()
        }
        return AndroidStats(0, 0, 0, "", false)
    }

    // Lyrics extraction
    fun extractLyrics(path: String): String {
        try {
            val rawMap = NativeTagLib.getMetadata(path) ?: return ""
            if (rawMap.isEmpty()) return ""

            var lyrics = rawMap["LYRICS"]
            if (lyrics.isNullOrBlank()) lyrics = rawMap["USLT"]
            if (lyrics.isNullOrBlank()) lyrics = rawMap["©LYR"]
            if (lyrics.isNullOrBlank()) lyrics = rawMap["TEXT"]

            return lyrics ?: ""
        } catch (e: Exception) {
            Log.e("TagLibHelper", "Failed to extract lyrics for $path", e)
            return ""
        }
    }

    // Artwork extraction
    fun extractPictures(path: String): List<TrackPicture> {
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
