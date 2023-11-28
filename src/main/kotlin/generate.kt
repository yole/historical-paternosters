package page.yole.paternosters

import org.apache.velocity.Template
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.TypeDescription
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.OutputStreamWriter
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import kotlin.io.path.*
import kotlin.reflect.KProperty

class Book {
    var title: String? = null
    var full_title: String? = null
    var year: Int? = null
    var url: String? = null
    var author: String? = null
    var author_url: String? = null

    val outPath: String
        get() = "books/${title?.lowercase()}.html"
}

class Books {
    var books: MutableList<Book> = mutableListOf()
}

class Language {
    var name: String? = null
    var family: String? = null
    var url: String? = null

    val outPath: String
        get() = "languages/${name?.lowercase()}.html"
}

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

    var baseSpecimen: Specimen? = null
    var lang: Language? = null

    val outPath: String
        get() = "$path.html"
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

fun generateSpecimen(specimen: Specimen, path: String) {
    val template = Velocity.getTemplate("specimen.vm")
    val context = contextFromObject(specimen)
    generateToFile(path, template, context)
}

fun generateBook(book: Book, allSpecimens: List<Specimen>, path: String) {
    val template = Velocity.getTemplate("book.vm")
    val context = contextFromObject(book)
    context.put("specimens", allSpecimens.filter { it.attestations.any { a -> a.book == book }})
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
        if (specimen.language == null) {
            errors.add("No language specified in ${specimen.path}")
        } else {
            specimen.lang = languages.find { it.name == specimen.language }
        }
        specimen.attestations.sortBy { it.book!!.year }
    }
    return errors
}

fun main() {
    val books = loadBooks("data/books.yml")
    val languages = loadLanguages("data/languages.yml")
    val allSpecimens = loadSpecimens("data")

    val rootFamily = groupLanguagesIntoFamilies(languages)

    val errors = resolveReferences(allSpecimens, books, languages)

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
}
