package io.github.kvmy666.duostatusbar.hook

/** Which stock occupant an iOS glyph stands in for. */
internal enum class IosPart { BATTERY, WIFI, CELL }

/** The glyph a future Rive/Canvas implementation draws. Kept separate from [IosPart] on purpose. */
internal enum class IosIconKind { BATTERY, WIFI, CELL }
