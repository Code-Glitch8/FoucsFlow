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
