
import upickle.default.*

object Constants{
    val BLOCK_SIZE = 10
    val SEGMENT_SIZE = 2

    val FAMILIES: Map[String, SegmentFamily] =
    Map(
        "empty" -> SegmentFamily(
            id = "empty",
            children = Map(
                "empty_default" -> Segment(
                    pattern = Vector(
                        Vector(".", "."),
                        Vector(".", ".")
                    )
                )
            ),
            right_weights = Map(
            "empty"          -> 1.00f,
            "platform_top"   -> 0.20f,
            "platform_left"  -> 0.08f,   
            "platform_full"  -> 0.05f,
            "platform_bottom"-> 0.03f
            ),
            bottom_weights = Map(
                "empty"           -> 2.00f,
                "platform_bottom" -> 0.30f,
                "platform_diag1"  -> 0.05f,
                "platform_diag2"  -> 0.05f
            )
        ),

        "platform_top" -> SegmentFamily(
            id = "platform_top",
            children = Map(
                "platform_top" -> Segment(
                    pattern = Vector(
                        Vector("P", "P"),
                        Vector(".", "."))
                )
            ),
            right_weights = Map(
                "empty"               -> 1.00f,
                "platform_top"        -> 0.08f,
                "platform_cube_top_right" -> 0.05f,
                "platform_cube_top_left"  -> 0.05f
            ),
            bottom_weights = Map(
                "empty"           -> 1.00f,
                "platform_bottom" -> 0.20f,
                "platform_top"    -> 0.00f
            )
        ),

        "platform_bottom" -> SegmentFamily(
            id = "platform_bottom",
            children = Map(
                "platform_bottom" -> Segment(
                    pattern = Vector(
                        Vector(".", "."),
                        Vector("P", "P"))
                ),
                "platform_bottom_spike" -> Segment(
                    pattern = Vector(
                        Vector(".", "S"),
                        Vector("P", "P"))
                ),
                "platform_bottom_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector("C", "."),
                        Vector("P", "P"))
                )
            ),
            right_weights = Map(
                "empty"                  -> 1.00f,
                "platform_bottom"        -> 0.05f,
                "platform_cube_bottom_right" -> 0.08f,
                "platform_cube_bottom_left"  -> 0.08f,
                "platform_diag2"         -> 0.15f,
                "platform_full"          -> 0.05f,
                "platform_left"          -> 0.05f,
                "platform_right"         -> 0.05f
            ),
            bottom_weights = Map(
                "empty"           -> 1.00f,
                "platform_top"   -> 0.03f
            )
        ),

        "platform_left" -> SegmentFamily(
            id = "platform_left",
            children = Map(
                "platform_left" -> Segment(
                    pattern = Vector(
                        Vector("P", "."),
                        Vector("P", "."))
                )
            ),
            right_weights = Map(
                "empty"                  -> 1.00f,
                "platform_cube_bottom_left" -> 0.08f,
                "platform_top"           -> 0.10f,
                "platform_bottom"        -> 0.10f,
                "platform_diag1"         -> 0.10f
            ),
            bottom_weights = Map(
                "platform_top"   -> 0.30f,
                "empty"          -> 1.00f
            )
        ),

        "platform_right" -> SegmentFamily(
            id = "platform_right",
            children = Map(
                "platform_right" -> Segment(
                    pattern = Vector(
                        Vector(".", "P"),
                        Vector(".", "P"))
                )
            ),
            right_weights = Map(
                "empty"                     -> 1.00f,
                "platform_top"              -> 0.30f,
                "platform_bottom"           -> 0.25f,
                "platform_diag1"            -> 0.20f,
                "platform_cube_bottom_left" -> 0.15f
            ),
            bottom_weights = Map(
                "empty"        -> 1.00f,
                "platform_top" -> 0.30f
            )
        ),

        "platform_full" -> SegmentFamily(
            id = "platform_full",
            children = Map(
                "platform_full" -> Segment(
                    pattern = Vector(
                        Vector("P", "P"),
                        Vector("P", "P"))
                )
            ),
            right_weights = Map(
                "empty"          -> 1.00f,
                "platform_left"  -> 0.20f,
                "platform_bottom"-> 0.10f,
                "platform_diag1" -> 0.10f,
                "platform_full"  -> 0.08f
            ),
            bottom_weights = Map(
                "empty"          -> 1.00f,
                "platform_full"  -> 0.05f
            )
        ),

        "platform_diag1" -> SegmentFamily(
            id = "platform_diag1",
            children = Map(
                "platform_diag1" -> Segment(
                    pattern = Vector(
                        Vector("P", "."),
                        Vector("P", "P"))
                ),
                "platform_diag1_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector("P", "C"),
                        Vector("P", "P"))
                ),
                "platform_diag1_spike" -> Segment(
                    pattern = Vector(
                        Vector("P", "S"),
                        Vector("P", "P"))
                )
            ),
            right_weights = Map(
                "empty"                     -> 1.00f,
                "platform_bottom"           -> 0.50f,
                "platform_diag2"            -> 0.35f,
                "platform_cube_bottom_left" -> 0.25f
            ),
            bottom_weights = Map(
                "empty"        -> 1.00f,
                "platform_top" -> 0.20f
            )
        ),

        "platform_diag2" -> SegmentFamily(
            id = "platform_diag2",
            children = Map(
                "platform_diag2" -> Segment(
                    pattern = Vector(
                        Vector(".", "P"),
                        Vector("P", "P"))
                ),
                "platform_diag2_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector("C", "P"),
                        Vector("P", "P"))
                ),
                "platform_diag2_spike" -> Segment(
                    pattern = Vector(
                        Vector("S", "P"),
                        Vector("P", "P"))
                )
            ),
            right_weights = Map(
                "empty"                   -> 1.00f,
                "platform_left"           -> 0.20f,
                "platform_full"           -> 0.20f,
                "platform_top"            -> 0.15f,
                "platform_diag1"          -> 0.20f,
                "platform_cube_top_left"  -> 0.15f,
                "platform_cube_top_right" -> 0.15f
            ),
            bottom_weights = Map(
                "empty"        -> 1.00f,
                "platform_top" -> 0.20f
            )
        ),

        "platform_cube_top_right" -> SegmentFamily(
            id = "platform_cube_top_right",
            children = Map(
                "platform_cube_top_right" -> Segment(
                    pattern = Vector(
                        Vector(".", "P"),
                        Vector(".", "."))
                )
            ),
            right_weights = Map(
                "empty"                   -> 1.00f,
                "platform_top"            -> 0.30f,
                "platform_diag1"          -> 0.15f,
                "platform_cube_top_left"  -> 0.20f
            ),
            bottom_weights = Map(
                "empty"           -> 1.00f,
                "platform_bottom" -> 0.30f,
                "platform_top"    -> 0.10f
            )
        ),

        "platform_cube_top_left" -> SegmentFamily(
            id = "platform_cube_top_left",
            children = Map(
                "platform_cube_top_left" -> Segment(
                    pattern = Vector(
                        Vector("P", "."),
                        Vector(".", "."))
                )
            ),
            right_weights = Map(
                "empty"          -> 1.00f,
                "platform_top"   -> 0.30f,
                "platform_diag1" -> 0.15f
            ),
            bottom_weights = Map(
                "empty"           -> 1.00f,
                "platform_bottom" -> 0.35f,
                "platform_top"    -> 0.10f
            )
        ),

        "platform_cube_bottom_right" -> SegmentFamily(
            id = "platform_cube_bottom_right",
            children = Map(
                "platform_cube_bottom_right" -> Segment(
                    pattern = Vector(
                        Vector(".", "."),
                        Vector(".", "P"))
                ),
                "platform_cube_bottom_right_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector(".", "C"),
                        Vector(".", "P"))
                ),
                "platform_cube_bottom_right_spike" -> Segment(
                    pattern = Vector(
                        Vector(".", "S"),
                        Vector(".", "P"))
                )
            ),
            right_weights = Map(
                "empty"                     -> 1.00f,
                "platform_bottom"           -> 0.30f,
                "platform_diag2"            -> 0.20f,
                "platform_full"             -> 0.05f,
                "platform_cube_bottom_left" -> 0.10f
            ),
            bottom_weights = Map(
                "empty"                   -> 1.00f,
                "platform_top"            -> 0.30f,
                "platform_cube_top_right" -> 0.25f
            )
        ),

        "platform_cube_bottom_left" -> SegmentFamily(
            id = "platform_cube_bottom_left",
            children = Map(
                "platform_cube_bottom_left" -> Segment(
                    pattern = Vector(
                        Vector(".", "."),
                        Vector("P", "."))
                ),
                "platform_cube_bottom_left_checkpoint" -> Segment(
                    pattern = Vector(
                        Vector("C", "."),
                        Vector("P", "."))
                ),
                "platform_cube_bottom_left_spike" -> Segment(
                    pattern = Vector(
                        Vector("S", "."),
                        Vector("P", "."))
                )
            ),
            right_weights = Map(
                "empty"           -> 1.00f,
                "platform_bottom" -> 0.30f,
                "platform_diag2"  -> 0.20f
            ),
            bottom_weights = Map(
                "empty"                   -> 1.00f,
                "platform_top"            -> 0.30f,
                "platform_cube_top_left"  -> 0.20f,
                "platform_diag1"          -> 0.15f
            )
        ),
        "death_floor" -> SegmentFamily(
            id = "death_floor",
            children = Map(
                "death_floor" -> Segment(
                    pattern = Vector(
                        Vector(".", "."),
                        Vector("S", "S")
                    )
                )
            ),
            right_weights = Map(
                "death_floor" -> 1.00f
            ),
            bottom_weights = Map(
                "platform_bottom" -> 0.50f,
                "platform_top" ->0.50f
            )
        )
    )

    val CHARACTERS: Map[String, PlayerStats] =
    Map(
        "Crumb" -> PlayerStats(speed = 120f, jumpForce = -200f, size = Vector2(9f, 9f)),
        "Spider" -> PlayerStats(speed = 120, jumpForce = -250f, size = Vector2(9f, 18f)),
        "Gnome" -> PlayerStats(speed = 120, jumpForce = -200, size = Vector2(9f, 9f)),
        "Stickman" -> PlayerStats(speed = 140, jumpForce = -200, size = Vector2(9f, 18f))
    )
}

case class Vector2
(
    val x: Float,
    val y: Float
)

case class InputState(
    moveLeft: Boolean = false,
    moveRight: Boolean = false,
    jump: Boolean = false
) derives ReadWriter

object InputState:
    val empty = InputState()
