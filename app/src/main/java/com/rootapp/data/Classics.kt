package com.rootapp.data

/**
 * A small curated set of public-domain classics (short excerpts) to read and listen to.
 * Excerpts only, from long-out-of-copyright authors.
 */
object Classics {
    data class Work(val title: String, val author: String, val kind: String, val excerpt: String)

    val works: List<Work> = listOf(
        Work(
            "Sonnet 18", "William Shakespeare", "Sonnet",
            "Shall I compare thee to a summer's day?\nThou art more lovely and more temperate.",
        ),
        Work(
            "The Road Not Taken", "Robert Frost", "Poem",
            "Two roads diverged in a wood, and I,\nI took the one less traveled by,\nand that has made all the difference.",
        ),
        Work(
            "Hope", "Emily Dickinson", "Poem",
            "Hope is the thing with feathers\nthat perches in the soul,\nand sings the tune without the words,\nand never stops at all.",
        ),
        Work(
            "The Tyger", "William Blake", "Poem",
            "Tyger Tyger, burning bright,\nin the forests of the night.",
        ),
        Work(
            "Daffodils", "William Wordsworth", "Poem",
            "I wandered lonely as a cloud\nthat floats on high o'er vales and hills.",
        ),
        Work(
            "If", "Rudyard Kipling", "Poem",
            "If you can keep your head when all about you\nare losing theirs and blaming it on you,\nyours is the Earth and everything that's in it.",
        ),
        Work(
            "Ozymandias", "Percy Bysshe Shelley", "Poem",
            "I met a traveller from an antique land\nwho said: two vast and trunkless legs of stone\nstand in the desert.",
        ),
        Work(
            "She Walks in Beauty", "Lord Byron", "Poem",
            "She walks in beauty, like the night\nof cloudless climes and starry skies.",
        ),
        Work(
            "Invictus", "William Ernest Henley", "Poem",
            "I am the master of my fate,\nI am the captain of my soul.",
        ),
        Work(
            "A Psalm of Life", "Henry Wadsworth Longfellow", "Poem",
            "Life is real! Life is earnest!\nAnd the grave is not its goal.",
        ),
        Work(
            "Fire and Ice", "Robert Frost", "Poem",
            "Some say the world will end in fire,\nsome say in ice.",
        ),
        Work(
            "Ode on a Grecian Urn", "John Keats", "Poem",
            "Beauty is truth, truth beauty, that is all\nye know on earth, and all ye need to know.",
        ),
    )

    /** A fresh random selection each call. */
    fun random(count: Int = 6): List<Work> = works.shuffled().take(count)
}
