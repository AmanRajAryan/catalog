package aman.catalog.audio.models

data class TrackPicture(
    val data: ByteArray,
    val mimeType: String,
    val description: String
) {
    // ByteArray doesn't support structural equality by default, so equals/hashCode
    // are implemented manually using contentEquals/contentHashCode.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TrackPicture

        if (!data.contentEquals(other.data)) return false
        if (mimeType != other.mimeType) return false
        return description == other.description
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + description.hashCode()
        return result
    }
}
