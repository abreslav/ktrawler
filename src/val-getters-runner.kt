import org.jetbrains.ktrawler.Ktrawler

fun Ktrawler.features(repoName: String): Map<String, Any> {
        fun perc(whole: Int, part: Int) = String.format("%.2f%% (%d)", 100.0 * part / whole, part)
        return mapOf<String, Any>(
                "Repo" to repoName,
                "Total val's" to vals.count,
                "With getter" to valsWithGetter.count,
                "% w/expression body" to perc(valsWithGetter.count, valsWithGetterExpressionBody.count),
                "Overrides with getter" to overrideValsWithGetter.count,
                "% overrides w/expression body" to perc(overrideValsWithGetter.count, overrideValsWithGetterExpressionBody.count)
        )
    }

fun main(args: Array<String>) {

    val repos = mapOf(
            "../kotlin" to "Kotlin",
            "../debug-projects/jetpeople" to "JB Project 1",
            "../debug-projects/JetProfile" to "JB Project 2",
            "../idea/src/ultimate" to "IntelliJ IDEA",
            "../debug-projects/ktor" to "Ktor"
    )


    val sb = StringBuilder()
    val titles = Ktrawler(true).features("").keys
    sb.append(titles.joinToString(" | ")).append("\n")
    sb.append(titles.map { "----" }.joinToString(" | ")).append("\n")

    for ((repo, repoName) in repos) {
        println("Started working on $repoName")
        val ktrawler = Ktrawler(true)
        ktrawler.analyzeRepository(repo)

        sb.append(ktrawler.features(repoName).values.joinToString(" | ")).append("\n")
    }

    println()
    println(sb)
}