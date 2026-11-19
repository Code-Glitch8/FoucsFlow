package com.focusflow.app.data.repo

import android.database.sqlite.SQLiteConstraintException
import com.focusflow.app.data.db.ChapterDao
import com.focusflow.app.data.db.ChapterEntity
import com.focusflow.app.data.db.SubjectDao
import com.focusflow.app.data.db.SubjectEntity
import com.focusflow.app.domain.TaskNormalizer
import com.focusflow.app.domain.Class11Chapters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

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
    }

    /**
     * Adds any default Class 11 chapter that is missing. Safe on existing installs:
     *  - inserts use IGNORE against the unique (subject, name) index, so nothing is duplicated and
     *    user-created chapters with the same name are left exactly as they are;
     *  - nothing is ever deleted or renamed;
     *  - it runs once per [Class11Chapters.SEED_VERSION], so a default chapter the user deletes
     *    does not keep coming back.
     * Returns how many chapters were inserted.
     */
    suspend fun ensureClass11Chapters(settings: com.focusflow.app.data.settings.AppSettings): Int {
        if (settings.class11SeedVersion.first() >= Class11Chapters.SEED_VERSION) return 0
        val added = seedChapters(Class11Chapters.bySubject)
        settings.setClass11SeedVersion(Class11Chapters.SEED_VERSION)
        return added
    }

    /** Core of the seeding, separated from the version gate so it can be tested directly. */
    suspend fun seedChapters(lists: Map<String, List<String>>): Int {
        val subjects = subjectDao.getAll()
        var inserted = 0
        for ((subjectName, chapters) in lists) {
            val subject = subjects.firstOrNull { it.name.equals(subjectName, ignoreCase = true) } ?: continue
            for (name in chapters) {
                if (chapterDao.insert(ChapterEntity(subjectId = subject.id, name = name)) != -1L) inserted++
            }
        }
        return inserted
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
    }
}
