@file:OptIn(ExperimentalPathApi::class)

package page.yole.paternosters

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
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

class Book {
    var title: String? = null
    var full_title: String? = null
    var year: Int? = null
    var url: String? = null
    var author: String? = null
    var author_url: String? = null
    var image: String? = null

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

class Language {
    var name: String? = null
    var family: String? = null
    var url: String? = null

    val outPath: String
        get() = "languages/${name?.lowercase()?.replace(' ', '-')}.html"
}

val uncertainLanguages = mutableMapOf<String, Language>()

class Languages {
    var languages: MutableList<Language> = mutableListOf()
}

class LanguageFamily(
    val name: String,
    val languages: MutableList<Language> = mutableListOf(),
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

    var book: Book? = null
}

class BibliographyEntry {
    var title: String? = null
    var author: String? = null
    var year: Int? = null
    var url: String? = null
}

class Specimen {
    var path: String? = null
    var language: String? = null
    var base: String? = null
    var attestations: MutableList<Attestation> = mutableListOf()
    var bibliography: MutableList<BibliographyEntry> = mutableListOf()
    var text: String? = null
    var notes: String? = null
    var footnotes: Map<String, String> = hashMapOf()
    var gloss: Map<String, String> = hashMapOf()

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
        val downloadPath = Paths.get("out/images", book.titleSlug + ".jpg")
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
            val imageLinks = volumeInfo["imageLinks"] as JsonObject
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

fun generateSpecimen(specimen: Specimen, path: String) {
    val template = Velocity.getTemplate("specimen.vm")
    val context = contextFromObject(specimen)
    context.put("text", specimen.text?.let { formatFootnotes(it) })
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
        .sortedBy { it.second.page ?: it.second.number }

    context.put("specimens", specimensWithAttestations)

    generateToFile(path, template, context)
}

fun generateBooks(books: List<Book>, path: String) {
    val template = Velocity.getTemplate("books.vm")
    val context = VelocityContext()
    context.put("books", books.sortedBy { it.year ?: 0 })
    generateToFile(path, template, context)
}

fun generateLanguage(language: Language, allSpecimens: List<Specimen>, path: String) {
    val template = Velocity.getTemplate("language.vm")
    val context = contextFromObject(language)
    context.put("specimens", allSpecimens.filter { it.lang == language })
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
        .takeWhile { !it.startsWith("#") } //next caption
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
    return errors
}

fun findOrCreateUncertainLanguage(language: String): Language {
    return uncertainLanguages.getOrPut(language) {
        Language().apply {
            name = language
            family = "Uncertainly Identified"
        }
    }
}

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
        generateLanguage(language, allSpecimens, "out/${language.outPath}")
    }
    generateBooks(books, "out/books.html")
    generateLanguages(rootFamily, "out/languages.html")
    generateIndex("out/index.html")
    Paths.get("templates/paternoster.css").copyTo(Paths.get("out/paternoster.css"), overwrite = true)
    Paths.get("images").copyToRecursively(Paths.get("out/images"), overwrite = true, followLinks = false)
}
