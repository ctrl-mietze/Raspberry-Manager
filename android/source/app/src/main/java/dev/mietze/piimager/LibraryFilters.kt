package dev.mietze.piimager

/** Model choices only match declared image tags. Unknown compatibility is not fabricated. */
object LibraryFilters {
 val boards = listOf("All models", "Pi 5", "Pi 4", "Pi 3", "Zero 2")
 fun matchesBoard(tags: List<String>, board: String): Boolean {
  if (board == "All models") return true
  val prefixes = when (board) {
   "Pi 5" -> listOf("pi5-")
   "Pi 4" -> listOf("pi4-")
   "Pi 3" -> listOf("pi3-")
   "Zero 2" -> listOf("pi3-") // Official Zero 2 W device profile shares these tags.
   else -> return false
  }
  return tags.any { tag -> prefixes.any { tag.startsWith(it, true) } }
 }
}
