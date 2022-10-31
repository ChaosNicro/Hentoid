package me.devsaki.hentoid.util.download

import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class RequestOrder_(
    val method: HttpMethod,
    val url: String,
    val headers: Map<String, String>,
    val site: Site,
    val targetDir: DocumentFile,
    val fileName: String,
    val pageIndex: Int,
    val backupUrl: String,
    val img : ImageFile/*,
    val parseListener: Consumer<Map<String, String>>,
    val errorListener: Consumer<NetworkError>*/
) {
    val killSwitch: AtomicBoolean = AtomicBoolean(false)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val req = other as RequestOrder_
        return url == req.url && method == req.method
    }

    override fun hashCode(): Int {
        return Objects.hash(url, method)
    }

    enum class HttpMethod {
        GET, POST, OPTIONS
    }

    enum class NetworkErrorType {
        INTERRUPTED, TIMEOUT, NO_CONNECTION, AUTH_FAILURE, SERVER_ERROR, NETWORK_ERROR, PARSE_ERROR
    }

    data class NetworkError(
        val statusCode: Int,
        val message: String,
        val type: NetworkErrorType
    )
}