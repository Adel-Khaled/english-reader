package com.englishreader.app.data.catalog

import com.englishreader.app.data.db.Difficulty
import com.englishreader.app.data.db.Difficulty.ADVANCED
import com.englishreader.app.data.db.Difficulty.BEGINNER
import com.englishreader.app.data.db.Difficulty.INTERMEDIATE

data class CatalogBook(
    val gutenbergId: Int,
    val title: String,
    val author: String,
    val category: String,
    val difficulty: Difficulty,
    val description: String,
) {
    val id: String get() = "pg$gutenbergId"
    val coverUrl: String get() = "https://www.gutenberg.org/cache/epub/$gutenbergId/pg$gutenbergId.cover.medium.jpg"
    val textUrl: String get() = "https://www.gutenberg.org/cache/epub/$gutenbergId/pg$gutenbergId.txt"
    val fallbackTextUrl: String get() = "https://www.gutenberg.org/ebooks/$gutenbergId.txt.utf-8"
}

/**
 * Hand-picked public-domain books from Project Gutenberg, graded for English learners.
 * Descriptions are original summaries written for this app.
 */
object Catalog {
    val books: List<CatalogBook> = listOf(
        CatalogBook(11, "Alice's Adventures in Wonderland", "Lewis Carroll", "Children", BEGINNER,
            "A curious girl falls down a rabbit hole into a world of talking animals and strange puzzles. Short chapters and playful language make it a gentle first novel."),
        CatalogBook(16, "Peter Pan", "J. M. Barrie", "Children", BEGINNER,
            "The boy who never grows up takes Wendy and her brothers to Neverland, where pirates, fairies and lost boys await."),
        CatalogBook(55, "The Wonderful Wizard of Oz", "L. Frank Baum", "Children", BEGINNER,
            "Dorothy is carried by a cyclone to the land of Oz and travels the yellow brick road with three unusual friends. Clear, simple sentences."),
        CatalogBook(21, "Aesop's Fables", "Aesop", "Short Stories", BEGINNER,
            "Hundreds of very short animal stories, each ending with a lesson. Perfect for reading a few minutes at a time."),
        CatalogBook(2591, "Grimms' Fairy Tales", "Jacob & Wilhelm Grimm", "Short Stories", BEGINNER,
            "Classic folk tales such as Hansel and Gretel and Snow White, told in a direct storytelling style."),
        CatalogBook(902, "The Happy Prince and Other Tales", "Oscar Wilde", "Short Stories", BEGINNER,
            "Beautiful, short fairy tales about kindness and sacrifice, written in elegant but accessible English."),
        CatalogBook(236, "The Jungle Book", "Rudyard Kipling", "Adventure", BEGINNER,
            "Mowgli, a boy raised by wolves, learns the law of the jungle from Baloo the bear and Bagheera the panther."),
        CatalogBook(113, "The Secret Garden", "Frances Hodgson Burnett", "Children", BEGINNER,
            "A lonely girl discovers a hidden, locked garden and brings it — and the people around her — back to life."),
        CatalogBook(271, "Black Beauty", "Anna Sewell", "Children", BEGINNER,
            "The life story of a horse, told in his own voice, from happy days in the country to hard work in the city."),
        CatalogBook(7256, "The Gift of the Magi", "O. Henry", "Short Stories", BEGINNER,
            "A very short, famous story about a young couple who each give up their greatest treasure to buy a gift for the other."),
        CatalogBook(45, "Anne of Green Gables", "L. M. Montgomery", "Novel", INTERMEDIATE,
            "A talkative, imaginative orphan girl is sent by mistake to live with an elderly brother and sister on a farm."),
        CatalogBook(120, "Treasure Island", "Robert Louis Stevenson", "Adventure", INTERMEDIATE,
            "Young Jim Hawkins finds a treasure map and sails with a crew that includes the clever pirate Long John Silver."),
        CatalogBook(74, "The Adventures of Tom Sawyer", "Mark Twain", "Adventure", INTERMEDIATE,
            "A mischievous boy on the Mississippi River gets into trouble, falls in love, and witnesses a crime."),
        CatalogBook(1661, "The Adventures of Sherlock Holmes", "Arthur Conan Doyle", "Mystery", INTERMEDIATE,
            "Twelve detective stories in which Sherlock Holmes solves puzzling cases with careful observation and logic."),
        CatalogBook(2852, "The Hound of the Baskervilles", "Arthur Conan Doyle", "Mystery", INTERMEDIATE,
            "Holmes and Watson investigate a legend about a monstrous dog that haunts an old family on the lonely moors."),
        CatalogBook(35, "The Time Machine", "H. G. Wells", "Science Fiction", INTERMEDIATE,
            "An inventor travels hundreds of thousands of years into the future and discovers what has become of humanity."),
        CatalogBook(36, "The War of the Worlds", "H. G. Wells", "Science Fiction", INTERMEDIATE,
            "Martians land in England and attack. A classic of science fiction told by a man trying to survive."),
        CatalogBook(103, "Around the World in Eighty Days", "Jules Verne", "Adventure", INTERMEDIATE,
            "Phileas Fogg bets his fortune that he can travel around the globe in eighty days, with trains, ships and an elephant."),
        CatalogBook(215, "The Call of the Wild", "Jack London", "Adventure", INTERMEDIATE,
            "Buck, a pet dog, is stolen and sold as a sled dog in the frozen Yukon, where he must learn to survive."),
        CatalogBook(43, "The Strange Case of Dr Jekyll and Mr Hyde", "Robert Louis Stevenson", "Horror", INTERMEDIATE,
            "A respected doctor's experiment releases a dark second self. A short, gripping novella."),
        CatalogBook(46, "A Christmas Carol", "Charles Dickens", "Novel", INTERMEDIATE,
            "The mean old Ebenezer Scrooge is visited by three spirits on Christmas Eve and learns to change his life."),
        CatalogBook(84, "Frankenstein", "Mary Shelley", "Horror", ADVANCED,
            "A young scientist creates a living being and then abandons it, with tragic results for both of them."),
        CatalogBook(345, "Dracula", "Bram Stoker", "Horror", ADVANCED,
            "Told through letters and diaries, a group of friends fight a powerful vampire who has come to England."),
        CatalogBook(1342, "Pride and Prejudice", "Jane Austen", "Romance", ADVANCED,
            "Elizabeth Bennet and the proud Mr Darcy misjudge each other in this witty novel of manners and marriage."),
        CatalogBook(1260, "Jane Eyre", "Charlotte Brontë", "Romance", ADVANCED,
            "An orphaned governess with a strong spirit falls in love with her mysterious employer, Mr Rochester."),
        CatalogBook(1400, "Great Expectations", "Charles Dickens", "Novel", ADVANCED,
            "Pip, a poor orphan, suddenly receives a fortune from an unknown benefactor and must learn what really matters."),
        CatalogBook(174, "The Picture of Dorian Gray", "Oscar Wilde", "Novel", ADVANCED,
            "A handsome young man stays young while his portrait ages and shows the marks of his choices."),
        CatalogBook(2701, "Moby-Dick", "Herman Melville", "Adventure", ADVANCED,
            "Captain Ahab leads his ship on an obsessive hunt for the great white whale that took his leg."),
        CatalogBook(5200, "Metamorphosis", "Franz Kafka", "Novel", ADVANCED,
            "Gregor Samsa wakes up one morning transformed into a giant insect. A short, strange and powerful story."),
        CatalogBook(219, "Heart of Darkness", "Joseph Conrad", "Novel", ADVANCED,
            "A sailor travels up the Congo River in search of a mysterious ivory trader. Rich, complex prose."),
    )

    val categories: List<String> = books.map { it.category }.distinct().sorted()

    fun find(id: String): CatalogBook? = books.firstOrNull { it.id == id }
}
