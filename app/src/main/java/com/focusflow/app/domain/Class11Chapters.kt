package com.focusflow.app.domain

/**
 * Default Class 11 chapter lists, following the current (rationalised) NCERT textbooks that CBSE
 * uses for 2026-27: Mathematics 14 chapters, Physics 14, Chemistry 9 (Chemistry Parts 1 and 2).
 * Chapters NCERT has removed from the current books (e.g. Mathematical Reasoning, Principle of
 * Mathematical Induction, States of Matter, Hydrogen, s-Block, Environmental Chemistry) are not
 * seeded; users can still add them by hand.
 */
object Class11Chapters {
    /** Bump when a list changes so new installs and updates pick up additions exactly once. */
    const val SEED_VERSION = 1

    val mathematics = listOf(
        "Sets",
        "Relations and Functions",
        "Trigonometric Functions",
        "Complex Numbers and Quadratic Equations",
        "Linear Inequalities",
        "Permutations and Combinations",
        "Binomial Theorem",
        "Sequences and Series",
        "Straight Lines",
        "Conic Sections",
        "Introduction to Three Dimensional Geometry",
        "Limits and Derivatives",
        "Statistics",
        "Probability",
    )

    val physics = listOf(
        "Units and Measurements",
        "Motion in a Straight Line",
        "Motion in a Plane",
        "Laws of Motion",
        "Work, Energy and Power",
        "System of Particles and Rotational Motion",
        "Gravitation",
        "Mechanical Properties of Solids",
        "Mechanical Properties of Fluids",
        "Thermal Properties of Matter",
        "Thermodynamics",
        "Kinetic Theory",
        "Oscillations",
        "Waves",
    )

    val chemistry = listOf(
        "Some Basic Concepts of Chemistry",
        "Structure of Atom",
        "Classification of Elements and Periodicity in Properties",
        "Chemical Bonding and Molecular Structure",
        "Thermodynamics",
        "Equilibrium",
        "Redox Reactions",
        "Organic Chemistry: Some Basic Principles and Techniques",
        "Hydrocarbons",
    )

    /** Built-in subject name -> default chapters. */
    val bySubject: Map<String, List<String>> = linkedMapOf(
        "Mathematics" to mathematics,
        "Physics" to physics,
        "Chemistry" to chemistry,
    )
}
