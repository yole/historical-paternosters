package page.yole.paternosters

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.github.difflib.DiffUtils
import com.github.difflib.patch.AbstractDelta
import com.github.difflib.patch.ChangeDelta
import com.github.difflib.patch.Chunk
import org.apache.velocity.Template
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.TypeDescription
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.OutputStreamWriter
import java.io.StringReader
import java.lang.Exception
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import kotlin.io.path.*
import kotlin.reflect.KProperty

private fun toSnippet(text: String?, words: Int) = text?.split(' ')?.take(words)?.joinToString(" ") ?: ""

class BibliographyEntry {
    var title: String? = null
    var author: String? = null
    var publication: String? = null
    var year: Int? = null
    var url: String? = null
    var page: Int? = null
}

class Book {
    var title: String? = null
    var full_title: String? = null
    var year: Int? = null
    var url: String? = null
    var author: String? = null
    var author_url: String? = null
    var image: String? = null
    var type: String? = null
    var bibliography: MutableList<BibliographyEntry> = mutableListOf()

    var texts = mutableListOf<Specimen>()

    val outPath: String
        get() = "books/${titleSlug}.html"

    val titleSlug: String
        get() = title?.lowercase()?.replace(' ', '-') ?: ""

    val fullTitleSnippet: String
        get() {
            val snippet = full_title?.let { toSnippet(it, 8) } ?: title ?: ""
            return if (snippet == full_title) snippet else "$snippet..."
        }
}

class Books {
    var books: MutableList<Book> = mutableListOf()
}

class BookType(val name: String, val books: MutableList<Book> = mutableListOf())

class Language {
    var name: String? = null
    var family: String? = null
    var url: String? = null

    var specimens: List<Specimen>? = null

    val outPath: String?
        get() =
            if (family == uncertainFamily)
                null
            else
                "languages/${name?.lowercase()?.replace(' ', '-')}.html"

    companion object {
        const val uncertainFamily = "Uncertainly Identified"
    }
}

val uncertainLanguages = mutableMapOf<String, Language>()

class Languages {
    var languages: MutableList<Language> = mutableListOf()
}

class LanguageFamily(
    val name: String,
    val languages: MutableSet<Language> = sortedSetOf(compareBy(Language::name)),
    val subFamilyMap: MutableMap<String, LanguageFamily> = hashMapOf()
) {
    val subFamilies: List<LanguageFamily>
        get() = subFamilyMap.values.sortedBy { it.name }
}

class Attestation {
    var book_title: String? = null
    var page: Int? = null
    var number: Int? = null
    var description: String? = null
    var source: String? = null
    var text_variant: String? = null

    var book: Book? = null
}

class Specimen {
    var path: String? = null
    var language: String? = null
    var script: String? = null
    var base: String? = null
    var attestations: MutableList<Attestation> = mutableListOf()
    var bibliography: MutableList<BibliographyEntry> = mutableListOf()
    var text: String? = null
    var notes: String? = null
    var text_variants: Map<String, String> = hashMapOf()
    var normalize: Map<String, String> = hashMapOf()
    var footnotes: Map<String, String> = hashMapOf()
    var gloss: Map<String, String> = hashMapOf()
    var poetry: Boolean? = false
    var diff: Boolean? = true

    var baseSpecimen: Specimen? = null
    var lang: Language? = null

    val outPath: String
        get() = "$path.html"

    val snippet: String
        get() = footnoteRegex.replace(toSnippet(text, 5), "")

    val earliestAttestation: Attestation
        get() = attestations.sortedBy { it.book?.year ?: Int.MAX_VALUE }.first()
}

fun loadSpecimen(path: Path): Specimen {
    val yaml = Yaml(Constructor(Specimen::class.java, LoaderOptions()))

    yaml.addTypeDescription(TypeDescription(Attestation::class.java).apply {
        setExcludes("book")
    })
    yaml.addTypeDescription(TypeDescription(Specimen::class.java).apply {
        setExcludes("baseSpecimen", "lang")
    })

    return yaml.load<Specimen>(path.inputStream()).also {
        it.path = path.toString().removePrefix("data/").removeSuffix(".yml")
    }
}

@OptIn(ExperimentalPathApi::class)
fun loadSpecimens(path: String): List<Specimen> {
    return Paths.get(path).walk().mapNotNull { childPath ->
        if (childPath.toString().endsWith("books.yml") || childPath.toString().endsWith("languages.yml"))
            null
        else
            loadSpecimen(childPath)

    }.toList()
}

fun loadBooks(path: String): List<Book> {
    val yaml = Yaml(Constructor(Books::class.java, LoaderOptions()))
    return yaml.load<Books>(Paths.get(path).inputStream()).books
}

fun loadLanguages(path: String): List<Language> {
    val yaml = Yaml(Constructor(Languages::class.java, LoaderOptions()))
    return yaml.load<Languages>(Paths.get(path).inputStream()).languages
}

private fun generateToFile(
    path: String,
    template: Template,
    context: VelocityContext
) {
    val outPath = Paths.get(path)
    outPath.parent.createDirectories()
    OutputStreamWriter(outPath.outputStream()).use { writer ->
        template.merge(context, writer)
    }
}

fun contextFromObject(obj: Any): VelocityContext {
    val context = VelocityContext()
    for (member in obj::class.members) {
        if (member is KProperty) {
            context.put(member.name, member.getter.call(obj))
        }
    }
    return context
}

fun markdownToHtml(text: String): String {
    val markdownFlavourDescriptor = GFMFlavourDescriptor()
    val tree = MarkdownParser(markdownFlavourDescriptor).buildMarkdownTreeFromString(text)
    return HtmlGenerator(text, tree, markdownFlavourDescriptor).generateHtml()
}

val footnoteRegex = Regex("\\[(\\d+)]")

fun formatFootnotes(text: String): String {
    return footnoteRegex.replace(text) { mr -> "<sup>${mr.groupValues[1]}</sup>"}
}

data class GlossedTextWord(val original: String, val gloss: String)
data class GlossedTextLine(val words: List<GlossedTextWord>)

fun formatGlossedText(specimen: Specimen): List<GlossedTextLine> {
    val text = specimen.text ?: return emptyList()
    var pos = 0
    val words = mutableListOf<GlossedTextWord>()
    while (pos < text.length) {
        while (pos < text.length && text[pos].isWhitespace()) {
            pos++
        }
        if (pos < text.length && text[pos] == '[') {
            pos++
            var footnote = ""
            while (pos < text.length && text[pos].isDigit()) {
                footnote += text[pos++]
            }
            if (pos < text.length && text[pos] == ']') {
                pos++
            }
            words[words.size-1] = GlossedTextWord(words.last().original + "<sup>$footnote</sup>", words.last().gloss)
        }

        val (word, gloss) = findLongestMatchingGloss(specimen.gloss, text, pos)
        if (word != null && gloss != null) {
            words.add(GlossedTextWord(word, gloss))
        }
        else {
            val wordEnd = text.indexOf(' ', pos).takeIf { it >= 0 } ?: text.length
            words.add(GlossedTextWord(text.substring(pos, wordEnd), ""))
        }
        pos += words.last().original.length
    }
    return breakIntoLines(words)
}

fun findLongestMatchingGloss(glosses: Map<String, String>, text: String, pos: Int): Pair<String?, String?> {
    var resultOriginal = ""
    var resultGloss = ""
    for ((original, gloss) in glosses) {
        if (text.startsWith(original, pos) && original.length > resultOriginal.length) {
            resultOriginal = original
            resultGloss = gloss
        }
    }
    return if (resultOriginal.isNotEmpty()) resultOriginal to resultGloss else null to null
}

fun breakIntoLines(words: List<GlossedTextWord>): List<GlossedTextLine> {
    val result = mutableListOf<GlossedTextLine>()
    var currentLineWords = mutableListOf<GlossedTextWord>()
    for (word in words) {
        if (currentLineWords.sumOf { it.original.length } + word.original.length > 50) {
            result.add(GlossedTextLine(currentLineWords))
            currentLineWords = mutableListOf()
        }
        currentLineWords.add(word)
    }
    if (currentLineWords.isNotEmpty()) {
        result.add(GlossedTextLine(currentLineWords))
    }
    return result
}

fun downloadThumbnail(book: Book): String? {
    val bookUrl = book.url ?: return null
    if ("books.google.com" in bookUrl) {
        val imagesPath = Paths.get("out/images")
        imagesPath.createDirectories()
        val downloadPath = imagesPath.resolve(book.titleSlug + ".jpg")
        if (!downloadPath.exists()) {
            val id = bookUrl.substringAfter("id=")
            val httpClient = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/books/v1/volumes/$id"))
                .build()
            val response = httpClient.send(request, BodyHandlers.ofString())

            val parser = Parser.default()
            val jsonObject = parser.parse(StringReader(response.body())) as JsonObject
            val volumeInfo = jsonObject["volumeInfo"] as JsonObject
            val imageLinks = volumeInfo["imageLinks"] as JsonObject? ?: return null
            val medium = imageLinks["medium"] as String

            val imageRequest = HttpRequest.newBuilder()
                .uri(URI.create(medium))
                .build()
            httpClient.send(imageRequest, BodyHandlers.ofFile(downloadPath))
        }

        return book.titleSlug + ".jpg"
    }
    return null
}

data class FootnoteDiff(val text: String) {
    val sources = mutableListOf<String>()
}

class FootnoteData(val wordIndex: Int) {
    val differences = arrayListOf<FootnoteDiff>()

    fun addDifference(changedText: String, sources: List<String>) {
        val footnoteDiff = differences.find { it.text == changedText }
            ?: FootnoteDiff(changedText).also { differences.add(it) }
        footnoteDiff.sources.addAll(sources)
    }

    fun formatAsString(): String =
        differences.joinToString("; ") {
            it.sources.joinToString(", ") + ": " + it.text
        }
}

fun isSimilarWord(w1: String, w2: String): Boolean {
    val (longerWord, shorterWord) = if (w1.length > w2.length) w1 to w2 else w2 to w1
    val differentCharacters = longerWord.toCharArray().toSet() - shorterWord.toCharArray().toSet()
    return differentCharacters.size < w1.length / 2
}

fun splitBySentenceBoundary(delta: AbstractDelta<String>): List<AbstractDelta<String>> {
    val sourceSentenceEnd = delta.source.lines.indexOfFirst { it.endsWith('.') }
    val targetSentenceEnd = delta.target.lines.indexOfFirst { it.endsWith('.') }
    if (sourceSentenceEnd != -1 && targetSentenceEnd != -1 &&
        sourceSentenceEnd != delta.source.size() - 1 &&
        targetSentenceEnd != delta.target.size() - 1)
    {
        return listOf(
            ChangeDelta(
                Chunk(delta.source.position, delta.source.lines.take(sourceSentenceEnd + 1)),
                Chunk(delta.target.position, delta.target.lines.take(targetSentenceEnd + 1))
            ),
            ChangeDelta(
                Chunk(
                    delta.source.position + sourceSentenceEnd + 1,
                    delta.source.lines.drop(sourceSentenceEnd + 1)
                ),
                Chunk(
                    delta.target.position + targetSentenceEnd + 1,
                    delta.target.lines.drop(targetSentenceEnd + 1)
                )
            )
        )
    }
    return listOf(delta)
}

fun splitIntoWords(delta: AbstractDelta<String>): List<AbstractDelta<String>> {
    val sourceLines = delta.source.lines.size
    if (sourceLines != delta.target.lines.size || sourceLines == 1) {
        return listOf(delta)
    }
    val pairs = delta.source.lines zip delta.target.lines
    if (pairs.all { isSimilarWord(it.first, it.second) }) {
        return pairs.withIndex().map { (index, pair) ->
            ChangeDelta(
                Chunk(delta.source.position + index, listOf(pair.first)),
                Chunk(delta.target.position + index, listOf(pair.second))
            )
        }
    }
    return listOf(delta)
}

fun ignoreDelta(delta: AbstractDelta<String>, normalizeMap: Map<String, String>): Boolean {
    fun normalize(string: String) =
        normalizeMap.entries.fold(string) { s, (key, value) -> s.replace(key, value)}

    val sourceText = delta.source.lines.joinToString(" ")
    val destinationText = delta.target.lines.joinToString(" ")
    return normalize(sourceText) == normalize(destinationText)
}

fun compareVariants(specimen: Specimen) {
    val footnotes = mutableListOf<FootnoteData>()

    fun footnoteForWord(footnoteTargetWord: Int): FootnoteData =
        footnotes.find { it.wordIndex == footnoteTargetWord }
            ?: FootnoteData(footnoteTargetWord).also { footnotes.add(it) }

    val baseWords = specimen.text!!.split(' ')

    for (textVariant in specimen.text_variants) {
        val sources = textVariant.key.split(',').map { it.trim() }
        val variantWords = textVariant.value.split(' ')
        val wordDiff = DiffUtils.diff(baseWords, variantWords)

        for (delta in wordDiff.deltas.flatMap { splitBySentenceBoundary(it) }.flatMap { splitIntoWords(it) }) {
            if (delta.target.lines.isEmpty()) {
                if (delta.source.position + delta.source.lines.size == baseWords.size) {
                    val footnote = footnoteForWord(delta.source.position - 1)
                    footnote.addDifference("text ends here", sources)
                }
                else if (delta.source.lines.size == 1) {
                    val footnote = footnoteForWord(delta.source.position)
                    footnote.addDifference("word omitted", sources)
                }
                else {
                    val footnote = footnoteForWord(delta.source.position)
                    val lastDeletedWord = delta.source.lines.last()
                    footnote.addDifference("text until '$lastDeletedWord' omitted", sources)
                }
            }
            else if (!ignoreDelta(delta, specimen.normalize)) {
                val footnoteTargetWord = delta.source.position + delta.source.lines.size - 1
                val footnote = footnoteForWord(footnoteTargetWord)
                val changedText = delta.target.lines.joinToString(" ")
                    .trimEnd(',', '.')
                footnote.addDifference(changedText, sources)
            }
        }
    }
    footnotes.sortBy { it.wordIndex }

    specimen.text = baseWords.withIndex().joinToString(" ") { (index, word) ->
        val footnoteIndex = footnotes.indexOfFirst { it.wordIndex == index }
        if (footnoteIndex < 0)
            word
        else
            "$word[${footnoteIndex + 1}]"
    }

    specimen.footnotes = footnotes.withIndex().associate { (index, footnote) ->
        (index + 1).toString() to footnote.formatAsString()
    }
}

fun generateSpecimen(specimen: Specimen, path: String) {
    if (specimen.text_variants.isNotEmpty()) {
        if (specimen.diff != false) {
            compareVariants(specimen)
        }
        for ((sources, textVariant) in specimen.text_variants) {
            val sourceNames = sources.split(',').map { it.trim() }
            for (sourceName in sourceNames) {
                val attestation = specimen.attestations.find { it.book_title == sourceName }
                attestation?.text_variant = textVariant
            }
        }
    }

    val template = Velocity.getTemplate("specimen.vm")
    val context = contextFromObject(specimen)
    context.put("text", specimen.text?.let {
        val withFootnotes = formatFootnotes(it)
        if (specimen.poetry == true) withFootnotes.replace("\n", "<br>") else withFootnotes
    })
    if (specimen.gloss.isNotEmpty()) {
        context.put("glossed_text", formatGlossedText(specimen))
    }
    context.put("notes", specimen.notes?.let { markdownToHtml(it) })
    generateToFile(path, template, context)
}

fun generateBook(book: Book, allSpecimens: List<Specimen>, path: String) {
    if (book.image == null) {
        try {
            book.image = downloadThumbnail(book)
        }
        catch(e: Exception) {
            e.printStackTrace()
        }
    }

    val template = Velocity.getTemplate("book.vm")
    val context = contextFromObject(book)
    val specimensWithAttestations = allSpecimens
        .mapNotNull { specimen ->
            val attestationInBook = specimen.attestations.find { a -> a.book == book }
            attestationInBook?.let { specimen to it }
        }
        .sortedBy { it.second.page?.let { p -> p * 100 + (it.second.number ?: 0) } ?: it.second.number }

    context.put("specimens", specimensWithAttestations)

    generateToFile(path, template, context)
}

fun generateBooks(books: List<Book>, path: String) {
    val template = Velocity.getTemplate("books.vm")
    val context = VelocityContext()

    val collections = BookType("Collections")
    val bibles = BookType("Bible Translations")
    val other = BookType("Other Books")

    for (book in books.sortedBy { it.year ?: 0 }) {
        when (book.type) {
            "collection" -> collections.books.add(book)
            "bible" -> bibles.books.add(book)
            else -> other.books.add(book)
        }
    }

    context.put("books", listOf(collections, bibles, other))
    generateToFile(path, template, context)
}

fun generateLanguage(language: Language, path: String) {
    val template = Velocity.getTemplate("language.vm")
    val context = contextFromObject(language)
    generateToFile(path, template, context)
}

fun generateLanguages(rootFamily: LanguageFamily, path: String) {
    val template = Velocity.getTemplate("languages.vm")
    val context = VelocityContext()
    context.put("rootFamily", rootFamily)
    generateToFile(path, template, context)
}

fun generateIndex(path: String) {
    val readme = Paths.get("README.md").readLines()
        .drop(1) // first caption line
        .dropWhile { it.isEmpty() } // blank lines
        .takeWhile { "End of index.html" !in it }
        .joinToString("\n")
    val readmeHtml = markdownToHtml(readme)

    val template = Velocity.getTemplate("index.vm")
    generateToFile(path, template, VelocityContext().apply {
        put("readme", readmeHtml)
    })
}

fun groupLanguagesIntoFamilies(languages: List<Language>): LanguageFamily {
    val rootFamily = LanguageFamily("")
    for (language in languages) {
        val family = language.family
        if (family == null) {
            rootFamily.languages.add(language)
            continue
        }
        val families = family.split('/')
        var thisFamily = rootFamily
        for (subFamily in families) {
            thisFamily = thisFamily.subFamilyMap.getOrPut(subFamily) { LanguageFamily(subFamily) }
        }
        thisFamily.languages.add(language)
    }
    return rootFamily
}

private fun resolveReferences(
    allSpecimens: List<Specimen>,
    books: List<Book>,
    languages: List<Language>
): ArrayList<String> {
    val errors = arrayListOf<String>()

    for (specimen in allSpecimens) {
        for (attestation in specimen.attestations) {
            if (attestation.book_title == null) {
                errors.add("No book title specified for attestation in ${specimen.path}")
            } else {
                val book = books.find { it.title == attestation.book_title }
                if (book == null) {
                    errors.add("Unknown book ${attestation.book_title} in ${specimen.path}")
                    continue
                }
                attestation.book = book
                book.texts.add(specimen)
            }
        }
        if (specimen.base != null) {
            specimen.baseSpecimen = allSpecimens.find { it.path == specimen.base }
            if (specimen.baseSpecimen == null) {
                errors.add("Can't resolve base path in ${specimen.path}")
            }
        }
        val language = specimen.language
        if (language == null) {
            errors.add("No language specified in ${specimen.path}")
        } else {
            specimen.lang = languages.find { it.name == language } ?:
            findOrCreateUncertainLanguage(language)
        }
        if (errors.isEmpty()) {
            specimen.attestations.sortBy { it.book!!.year }
        }
    }

    for (language in languages + uncertainLanguages.values) {
        language.specimens = allSpecimens.filter { it.lang == language }
    }

    return errors
}

fun findOrCreateUncertainLanguage(language: String): Language {
    return uncertainLanguages.getOrPut(language) {
        Language().apply {
            name = language
            family = Language.uncertainFamily
        }
    }
}

@OptIn(ExperimentalPathApi::class)
fun main() {
    val books = loadBooks("data/books.yml")
    val languages = loadLanguages("data/languages.yml")
    val allSpecimens = loadSpecimens("data")

    val errors = resolveReferences(allSpecimens, books, languages)

    val rootFamily = groupLanguagesIntoFamilies(languages + uncertainLanguages.values)

    if (errors.isNotEmpty()) {
        for (error in errors) {
            println(error)
        }
        System.exit(1)
    }

    val p = Properties()
    p.setProperty("resource.loader.file.path", Paths.get("templates").toAbsolutePath().toString())
    Velocity.init(p)

    for (specimen in allSpecimens) {
        generateSpecimen(specimen, "out/${specimen.outPath}")
    }
    for (book in books) {
        generateBook(book, allSpecimens, "out/${book.outPath}")
    }
    for (language in languages) {
        generateLanguage(language, "out/${language.outPath}")
    }
    generateBooks(books, "out/books.html")
    generateLanguages(rootFamily, "out/languages.html")
    generateIndex("out/index.html")
    Paths.get("templates/paternoster.css").copyTo(Paths.get("out/paternoster.css"), overwrite = true)
    Paths.get("images").copyToRecursively(Paths.get("out/images"), overwrite = true, followLinks = false)
}
