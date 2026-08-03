package org.akkirrai.beakokit.api

/**
 * Resolves a user-provided repository link to the HTTPS URL of its index file.
 *
 * A GitHub repository root is intentionally not accepted: the index file location is part of
 * the repository contract and must be supplied explicitly by the caller.
 */
class SourceRepositoryUrlResolver {
    fun resolve(input: String): SourceRepositoryEndpoint {
        val url = input.trim()
        require(url.isNotEmpty()) { "Repository URL must not be blank" }

        return try {
            when {
                url.startsWith("https://github.com/", ignoreCase = true) ->
                    SourceRepositoryEndpoint(resolveGithubUrl(url))
                url.startsWith("https://raw.githubusercontent.com/", ignoreCase = true) ->
                    SourceRepositoryEndpoint(url)
                else -> SourceRepositoryEndpoint(url)
            }
        } catch (error: IllegalArgumentException) {
            throw SourceRepositoryUrlException(error.message ?: "Invalid repository URL", error)
        }
    }

    private fun resolveGithubUrl(url: String): String {
        require('?' !in url && '#' !in url) {
            "GitHub repository URL must not contain a query or fragment"
        }
        val path = url.substring("https://github.com/".length)
            .split('/')
            .filter(String::isNotEmpty)
        require(path.size >= 4) {
            "GitHub URL must point to a repository index file"
        }
        val owner = path[0]
        val repository = path[1].removeSuffix(".git")
        val kind = path[2]
        require(kind == "blob" || kind == "raw") {
            "GitHub URL must use /blob/ or /raw/ for the repository index file"
        }
        val ref = path[3]
        val filePath = path.drop(4).joinToString("/")
        require(owner.isNotBlank() && repository.isNotBlank() && ref.isNotBlank() && filePath.isNotBlank()) {
            "GitHub URL must include owner, repository, ref, and index file path"
        }
        return "https://raw.githubusercontent.com/$owner/$repository/$ref/$filePath"
    }
}

class SourceRepositoryUrlException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
