package site.seaunit.shuati.data.repo

import kotlinx.serialization.builtins.ListSerializer
import site.seaunit.shuati.core.ApiClient
import site.seaunit.shuati.data.dto.StatsMe
import site.seaunit.shuati.data.dto.WrongBookItem

/** 错题本与统计（契约 §4） */
class ReviewRepository(private val api: ApiClient) {

    suspend fun wrongBook(): List<WrongBookItem> =
        api.get("/api/wrong-book", ListSerializer(WrongBookItem.serializer())).orEmpty()

    suspend fun stats(): StatsMe = api.get("/api/stats/me", StatsMe.serializer()) ?: StatsMe()
}
