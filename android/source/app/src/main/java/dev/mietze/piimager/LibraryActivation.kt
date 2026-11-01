package dev.mietze.piimager

/** Pure state policy; enabling the library never performs I/O or downloads. */
object LibraryActivation {
 fun enabledSources(enable: Boolean, neverActivated: Boolean, current: Set<String>): Set<String> =
  if (enable && neverActivated) LibraryDefaults.ids else current.toSet()
}
