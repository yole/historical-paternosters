package page.yole.paternosters.algolia

import com.algolia.search.client.ClientSearch
import com.algolia.search.model.APIKey
import com.algolia.search.model.ApplicationID
import com.algolia.search.model.IndexName
import com.algolia.search.model.ObjectID
import com.algolia.search.model.indexing.Indexable
import kotlinx.serialization.Serializable
import page.yole.paternosters.Paternosters
import page.yole.paternosters.loadData
import page.yole.paternosters.parseInlineGlosses
import page.yole.paternosters.resolveReferences

@Serializable
private data class SpecimenRecord(
    val text: String,
    val language: String?,
    val book: String?,
    override val objectID: ObjectID
) : Indexable

private fun generateIndexRecords(paternosters: Paternosters): List<SpecimenRecord> {
    return paternosters.allSpecimens.mapNotNull { specimen ->
        val text = specimen.text ?: specimen.text_variants.values.first()
        val path = specimen.path
        val cleanText = parseInlineGlosses(text).joinToString(" ") { word -> word.original }
        if (path != null)
            SpecimenRecord(
                cleanText,
                specimen.language,
                specimen.earliestAttestation.bookRef?.full_title,
                ObjectID(path)
            )
        else
            null
    }
}

suspend fun main() {
    val paternosters = loadData()
    val errors = resolveReferences(paternosters)

    if (errors.isNotEmpty()) {
        for (error in errors) {
            println(error)
        }
        System.exit(1)
    }

    val records = generateIndexRecords(paternosters)

    val client = ClientSearch(
        applicationID = ApplicationID(System.getenv("ALGOLIA_APP_ID")),
        apiKey = APIKey(System.getenv("ALGOLIA_API_KEY"))
    )

    val index = client.initIndex(IndexName("paternosters"))
    index.run {
        saveObjects(SpecimenRecord.serializer(), records)
    }
}
