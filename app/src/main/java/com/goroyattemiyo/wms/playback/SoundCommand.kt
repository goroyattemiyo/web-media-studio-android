package com.goroyattemiyo.wms.playback

internal object SoundCommand {
    private const val PREFIX = "com.goroyattemiyo.wms.playback.SOUND_"
    const val VOLUME = PREFIX + "VOLUME"
    const val BOOST = PREFIX + "BOOST"
    const val BOOST_DB = PREFIX + "BOOST_DB"
    const val EQ_ENABLED = PREFIX + "EQ_ENABLED"
    const val EQ_BAND = PREFIX + "EQ_BAND"
    const val PRESET = PREFIX + "PRESET"
    const val SAVE = PREFIX + "SAVE"
    const val RENAME = PREFIX + "RENAME"
    const val DELETE = PREFIX + "DELETE"
    const val RESET = PREFIX + "RESET"
    const val PERCENT = "percent"
    const val ENABLED = "enabled"
    const val BOOST_DB_TENTHS = "boostDbTenths"
    const val INDEX = "index"
    const val GAIN_MB = "gainMb"
    const val ID = "id"
    const val NAME = "name"

    val actions = listOf(VOLUME, BOOST, BOOST_DB, EQ_ENABLED, EQ_BAND, PRESET, SAVE, RENAME, DELETE, RESET)
}
