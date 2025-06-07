package page.yole.paternosters

import eu.interedition.collatex.CollationAlgorithmFactory
import eu.interedition.collatex.Token
import eu.interedition.collatex.VariantGraph
import eu.interedition.collatex.Witness
import eu.interedition.collatex.matching.EqualityTokenComparator
import eu.interedition.collatex.simple.SimpleToken
import eu.interedition.collatex.simple.SimpleVariantGraphSerializer
import eu.interedition.collatex.simple.SimpleWitness
import eu.interedition.collatex.util.ParallelSegmentationApparatus
import eu.interedition.collatex.util.VariantGraphRanking
import java.io.File
import java.io.Writer
import java.util.*
import kotlin.system.exitProcess

fun normalizeToken(token: String): String {
    return token.lowercase().removeDiacritics()
}

fun String.removePunctuation(): String {
    return punctuation.fold(this) { acc, c -> acc.replace(c.toString(), "") }
}

fun uniqueSigil(text: String, allTexts: Collection<String>): String {
    for (i in 1..<text.length) {
        val prefix = text.substring(0, i)
        if (allTexts.count { it.startsWith(prefix) } == 1) {
            return prefix
        }
    }
    return text
}

fun exportNexusData(graph: VariantGraph, writer: Writer) {
    val matrix = mutableMapOf<String, StringBuilder>()
    var maxSymbols = 0
    ParallelSegmentationApparatus.generate(VariantGraphRanking.of(graph), object : ParallelSegmentationApparatus.GeneratorCallback {
        override fun start() {
        }

        override fun segment(contents: SortedMap<Witness, Iterable<Token>>) {
            val tokenMap = mutableMapOf<String, String>()
            for ((witness, tokens) in contents) {
                val builder = matrix.getOrPut(witness.sigil) { StringBuilder() }
                val text = tokens.joinToString("") { normalizeToken((it as SimpleToken).content) }
                if (text.isEmpty()) {
                    builder.append("?")
                }
                else {
                    val symbol = tokenMap.getOrPut(text) { ('A' + tokenMap.size).toString() }
                    builder.append(symbol)
                }
            }
            if (tokenMap.size > maxSymbols) {
                maxSymbols = tokenMap.size
            }
        }

        override fun end() {
            writer.write("begin data;\n")
            val symbols = StringBuilder()
            for (i in 0..<maxSymbols) {
                symbols.append('A' + i)
            }
            writer.write("\tdimensions ntax = ${matrix.size} nchar = ${matrix.values.first().length};\n")
            writer.write("\tformat symbols = \"$symbols\" labels = left;\n")
            writer.write("\tmatrix")
            for ((witness, tokens) in matrix) {
                writer.write("\n\t\t$witness\t$tokens")
            }
            writer.write(";\nend;\n")
            writer.flush()
        }
    })
}

fun exportDotFile(graph: VariantGraph, writer: Writer) {
    SimpleVariantGraphSerializer(graph).toDot(writer)
    writer.flush()
}

fun main(args: Array<String>) {
    if (args.size != 3) {
        println("Usage: CollateKt <input file> <nexus file> <dot file>")
        exitProcess(1)
    }
    val paternosters = loadData()
    val specimen = paternosters.allSpecimens.find { it.outPath.startsWith(args[0]) }
        ?: run {
            println("Couldn't find specimen ${args[1]}")
            exitProcess(1)
        }

    val variantGraph = VariantGraph()
    val comparator = EqualityTokenComparator()
    val algorithm = CollationAlgorithmFactory.dekker(comparator)

    val witnesses = mutableListOf<SimpleWitness>()
//    witnesses.add(SimpleWitness("base", specimen.text))
    for ((key, value) in specimen.text_variants) {
        witnesses.add(SimpleWitness(uniqueSigil(key, specimen.text_variants.keys), value.removePunctuation().removeDiacritics()))
    }
    algorithm.collate(variantGraph, witnesses)

    exportNexusData(variantGraph, File(args[1]).writer())

    VariantGraph.JOIN.apply(variantGraph)
    exportDotFile(variantGraph, File(args[2]).writer())
}
