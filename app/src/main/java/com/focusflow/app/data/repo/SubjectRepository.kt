package com.focusflow.app.data.repo

import android.database.sqlite.SQLiteConstraintException
import com.focusflow.app.data.db.ChapterDao
import com.focusflow.app.data.db.ChapterEntity
import com.focusflow.app.data.db.SubjectDao
import com.focusflow.app.data.db.SubjectEntity
import com.focusflow.app.domain.TaskNormalizer
import kotlinx.coroutines.flow.Flow

enum class EditResult { OK, BLANK, DUPLICATE, NOT_ALLOWED }

class SubjectRepository(
    private val subjectDao: SubjectDao,
    private val chapterDao: ChapterDao,
) {
    val subjects: Flow<List<SubjectEntity>> = subjectDao.observeAll()
    val allChapters: Flow<List<ChapterEntity>> = chapterDao.observeAll()

    fun chapters(subjectId: Long): Flow<List<ChapterEntity>> = chapterDao.observeForSubject(subjectId)

    /** Idempotent: inserts the built-in subjects that are missing (unique name makes it safe). */
    suspend fun ensureBuiltIns() {
        BUILT_IN_SUBJECTS.forEach { subjectDao.insert(SubjectEntity(name = it, builtIn = true)) }

        // Seed the current NCERT/CBSE Class 11 chapter set once. Existing user chapters are
        // retained, and case-insensitive unique indexes make this safe on every app launch.
        val subjectsByName = subjectDao.getAll().associateBy { it.name.lowercase() }
        val existing = chapterDao.getAll().map { it.subjectId to it.name.lowercase() }.toSet()
        CLASS_11_CHAPTERS.forEach { (subjectName, names) ->
            val subjectId = subjectsByName[subjectName.lowercase()]?.id ?: return@forEach
            names.filter { (subjectId to it.lowercase()) !in existing }.forEach { name ->
                chapterDao.insert(ChapterEntity(subjectId = subjectId, name = name))
            }
        }
    }

    suspend fun addSubject(rawName: String): EditResult {
        val name = TaskNormalizer.clean(rawName)
        if (name.isEmpty()) return EditResult.BLANK
        return if (subjectDao.insert(SubjectEntity(name = name)) == -1L) EditResult.DUPLICATE else EditResult.OK
    }

    suspend fun renameSubject(subject: SubjectEntity, rawName: String): EditResult {
        if (subject.builtIn) return EditResult.NOT_ALLOWED
        val name = TaskNormalizer.clean(rawName)
        if (name.isEmpty()) return EditResult.BLANK
        return try {
            subjectDao.update(subject.copy(name = name))
            EditResult.OK
        } catch (e: SQLiteConstraintException) {
            EditResult.DUPLICATE
        }
    }

    /** Deleting a subject also deletes its chapters (foreign-key cascade). Built-ins are kept. */
    suspend fun deleteSubject(subject: SubjectEntity): EditResult {
        if (subject.builtIn) return EditResult.NOT_ALLOWED
        subjectDao.delete(subject)
        return EditResult.OK
    }

    suspend fun addChapter(subjectId: Long, rawName: String): EditResult {
        val name = TaskNormalizer.clean(rawName)
        if (name.isEmpty()) return EditResult.BLANK
        return if (chapterDao.insert(ChapterEntity(subjectId = subjectId, name = name)) == -1L) {
            EditResult.DUPLICATE
        } else {
            EditResult.OK
        }
    }

    suspend fun renameChapter(chapter: ChapterEntity, rawName: String): EditResult {
        val name = TaskNormalizer.clean(rawName)
        if (name.isEmpty()) return EditResult.BLANK
        return try {
            chapterDao.update(chapter.copy(name = name))
            EditResult.OK
        } catch (e: SQLiteConstraintException) {
            EditResult.DUPLICATE
        }
    }

    suspend fun setChapterConfidence(chapter: ChapterEntity, confidence: Int?) {
        chapterDao.update(chapter.copy(selfConfidence = confidence?.coerceIn(1, 5)))
    }

    suspend fun deleteChapter(chapter: ChapterEntity) = chapterDao.delete(chapter)

    companion object {
        val BUILT_IN_SUBJECTS = listOf("Mathematics", "Physics", "Chemistry", "Biology", "Other")

        val CLASS_11_CHAPTERS = linkedMapOf(
            "Mathematics" to listOf(
                "Sets", "Relations and Functions", "Trigonometric Functions",
                "Complex Numbers and Quadratic Equations", "Linear Inequalities",
                "Permutations and Combinations", "Binomial Theorem", "Sequences and Series",
                "Straight Lines", "Conic Sections", "Introduction to Three Dimensional Geometry",
                "Limits and Derivatives", "Statistics", "Probability",
            ),
            "Physics" to listOf(
                "Units and Measurements", "Motion in a Straight Line", "Motion in a Plane",
                "Laws of Motion", "Work, Energy and Power", "System of Particles and Rotational Motion",
                "Gravitation", "Mechanical Properties of Solids", "Mechanical Properties of Fluids",
                "Thermal Properties of Matter", "Thermodynamics", "Kinetic Theory",
                "Oscillations", "Waves",
            ),
            "Chemistry" to listOf(
                "Some Basic Concepts of Chemistry", "Structure of Atom",
                "Classification of Elements and Periodicity in Properties",
                "Chemical Bonding and Molecular Structure", "Chemical Thermodynamics",
                "Equilibrium", "Redox Reactions", "Organic Chemistry: Some Basic Principles and Techniques",
                "Hydrocarbons",
            ),
        )
    }
}
