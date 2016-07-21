package org.jetbrains.ktrawler

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.addKotlinSourceRoot
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance
import org.kohsuke.args4j.Argument
import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option
import retrofit.RestAdapter
import retrofit.http.GET
import retrofit.http.Query
import java.io.File

data class Repo(val full_name: String, val git_url: String)

data class ResultPage(val total_count: Int, val items: List<Repo>)

interface Github {
    @GET("/search/repositories")
    fun searchRepositories(@Query("q") query: String, @Query("page") page: Int, @Query("per_page") perPage: Int): ResultPage
}

class GithubRepositoryCollector() {
    val restAdapter = RestAdapter.Builder()
        .setEndpoint("https://api.github.com")
        .build()
    val github = restAdapter.create(Github::class.java)

    fun processAllKotlinRepositories(callback: (Repo, Int, Int) -> Boolean) {
        var currentPage = 1
        var totalCount = -1
        var processedCount = 0
        var currentRepo = 0
        while (totalCount == -1 || processedCount < totalCount) {
            val repos = github.searchRepositories("language:kotlin", currentPage++, 100)
            totalCount = repos.total_count
            processedCount += repos.items.size
            for (it in repos.items) {
                currentRepo++
                if (!callback(it, currentRepo, totalCount)) return
            }
        }
    }
}

class Korpus(val baseDir: String) {
    fun update(local: Boolean, callback: (String) -> Boolean) {
        val collector = GithubRepositoryCollector()
        collector.processAllKotlinRepositories { url, current, total ->
            print("[$current/$total] ")
            (local || cloneOrUpdateRepository(url)) && callback(pathTo(url).path)
        }
    }

    fun cloneOrUpdateRepository(repo: Repo): Boolean {
        if (pathTo(repo).exists()) {
            updateRepository(repo)
        } else {
            if (!cloneRepository(repo)) return false
        }
        return true
    }

    private fun pathTo(repo: Repo): File = File(baseDir, repo.full_name)

    private fun cloneRepository(repo: Repo): Boolean {
        val owner = repo.full_name.substringBefore('/')
        val ownerDir = File(baseDir, owner)
        ownerDir.mkdirs()
        println("Cloning repo ${repo.full_name}")
        val process = Runtime.getRuntime().exec("git clone ${repo.git_url}", null, ownerDir)
        val result = process.waitFor()
        if (result != 0) {
            println("Repo clone failed: exit code ${result}")
            return false
        }
        Thread.sleep(15000)
        return true
    }

    private fun updateRepository(repo: Repo) {
        println("Updating repo ${repo.full_name}")
        val process = Runtime.getRuntime().exec("git pull", null, pathTo(repo))
        val result = process.waitFor()
        if (result != 0) {
            println("Repo update failed: exit code ${result}")
        }
        Thread.sleep(5000)
    }
}

data class FeatureUsage(val project: String, val file: String?, val line: Int)

class FeatureUsageCounter(val name: String, val trackUsages: Boolean = false) {
    val projects = hashSetOf<String>()
    val usages = arrayListOf<FeatureUsage>()
    var count = 0

    fun increment(projectPath: String, usage: PsiElement) {
        count++
        projects.add(projectPath)
        if (trackUsages) {
            val path = usage.containingFile?.virtualFile?.path
            val doc = PsiDocumentManager.getInstance(usage.project).getDocument(usage.containingFile)
            val line = if (doc != null) doc.getLineNumber(usage.textRange.startOffset) + 1 else 0
            usages.add(FeatureUsage(projectPath, path?.removePrefix(projectPath), line))
        }
    }

    fun report() {
        println("$name: $count in ${projects.size} projects")
        usages.forEach {
            println("  Project: ${it.project}; path: ${it.file}:${it.line}")
        }
    }
}

class Ktrawler(statsOnly: Boolean): KtTreeVisitorVoid() {
    var currentRepo: String? = null
    var repositoriesAnalyzed = 0
    var filesAnalyzed = 0
    var linesAnalyzed = 0
    val syntaxErrors = FeatureUsageCounter("Error count", !statsOnly)
    val delegationBySpecifiers = FeatureUsageCounter("'by' delegations", !statsOnly)
    val classes = FeatureUsageCounter("Classes")
    val innerClasses = FeatureUsageCounter("Inner classes")
    val innerClassesWithOuterTypeParameters = FeatureUsageCounter("Inner classes with outer type parameters", !statsOnly)
    val companionObjects = FeatureUsageCounter("Companion objects")
    val objects = FeatureUsageCounter("Object declarations")
    val topLevelObjects = FeatureUsageCounter("Top-level object declarations")
    val enums = FeatureUsageCounter("Enum classes")
    val enumsWithConstructorParameters = FeatureUsageCounter("Enum classes with constructor parameters")
    val enumsWithEntriesAndMembersMixed = FeatureUsageCounter("Enum classes with entries and members mixed", !statsOnly)
    val enumEntries = FeatureUsageCounter("Enum entries")
    val enumEntriesWithBody = FeatureUsageCounter("Enum entries with body")
    val functions = FeatureUsageCounter("Functions")
    val inlineFunctions = FeatureUsageCounter("Inline functions")
    val extensionFunctions = FeatureUsageCounter("Extension functions")
    val extensionFunctionsInClasses = FeatureUsageCounter("Extension functions inside classes")
    val lambdas = FeatureUsageCounter("Lambdas")
    val lambdasWithDeclaredReturnType = FeatureUsageCounter("Lambdas with declared return type", !statsOnly)
    val labeledExpressions = FeatureUsageCounter("Labeled expressions", !statsOnly)
    val qualifiedBreakContinue = FeatureUsageCounter("'break' or 'continue' with label", !statsOnly)
    val returnWithLabel = FeatureUsageCounter("'return' with label", !statsOnly)
    val whileLoops = FeatureUsageCounter("'while' loops")
    val doWhileLoops = FeatureUsageCounter("'do/while' loops")
    val whenWithExpression = FeatureUsageCounter("'when' with expression")
    val whenWithoutExpression = FeatureUsageCounter("'when' without expression")
    val whenConditionInRange = FeatureUsageCounter("'in' condition in 'when'")
    val primaryConstructorVisibility = FeatureUsageCounter("Primary constructors with non-default visibility")
    val vals = FeatureUsageCounter("'val' declarations")
    val vars = FeatureUsageCounter("'var' declarations")
    val typeParameters = FeatureUsageCounter("Type parameters")
    val typeParametersWithVariance = FeatureUsageCounter("Type parameters with variance")
    val typeArguments = FeatureUsageCounter("Type arguments")
    val typeArgumentsWithVariance = FeatureUsageCounter("Type arguments with variance")
    val typeArgumentsWithStar = FeatureUsageCounter("Type arguments with <*>")
    val rangeOperators = FeatureUsageCounter("Range operators")
    val typesAfterColon = FeatureUsageCounter("Types after colon")
    val backingFields = FeatureUsageCounter("Backing fields")

    fun analyzeRepository(rootPath: String) {
        currentRepo = rootPath
        repositoriesAnalyzed++
        val root = Disposable { }
        val configuration = CompilerConfiguration()
        configuration.addKotlinSourceRoot(rootPath)
        val environment = KotlinCoreEnvironment.createForProduction(root, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
        environment.getSourceFiles().forEach {
            if ("/testData/" !in it.virtualFile.path) {
                filesAnalyzed++
                linesAnalyzed += PsiDocumentManager.getInstance(it.project).getDocument(it)!!.lineCount
                it.acceptChildren(this)
            }
        }
        Disposer.dispose(root)
    }

    fun FeatureUsageCounter.increment(element: PsiElement) = increment(currentRepo!!, element)

    override fun visitErrorElement(element: PsiErrorElement) {
        super.visitErrorElement(element)
        syntaxErrors.increment(element)
    }

    private fun KtClass.hasEnumEntriesAndMembersMixed(): Boolean {
        var insideEntries = true
        for (declaration in declarations) {
            if (declaration is KtEnumEntry) {
                if (!insideEntries) return true
            }
            else {
                insideEntries = false
            }
        }
        return false
    }

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        classes.increment(klass)
        if (klass.isInner()) {
            innerClasses.increment(klass)
            if (klass.hasOuterTypeParameters()) {
                innerClassesWithOuterTypeParameters.increment(klass)
            }
        }
        if (klass.getPrimaryConstructorModifierList() != null) {
            primaryConstructorVisibility.increment(klass)
        }
        if (klass.isEnum()) {
            enums.increment(klass)
            if (klass.getPrimaryConstructorParameters().size > 0) {
                enumsWithConstructorParameters.increment(klass)
            }
            if (klass.hasEnumEntriesAndMembersMixed()) {
                enumsWithEntriesAndMembersMixed.increment(klass)
            }
        }
    }

    private fun KtClass.hasOuterTypeParameters(): Boolean {
        var cls = this
        while (true) {
            val parent = PsiTreeUtil.getParentOfType(cls, KtClass::class.java, true)
            if (parent == null) return false
            if (parent.typeParameters.isNotEmpty()) return true
            cls = parent
        }
    }

    override fun visitEnumEntry(enumEntry: KtEnumEntry) {
        super.visitEnumEntry(enumEntry)
        enumEntries.increment(enumEntry)
        if (enumEntry.getBody() != null) {
            enumEntriesWithBody.increment(enumEntry)
        }
    }

    override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        super.visitObjectDeclaration(declaration)
        objects.increment(declaration)
        if (declaration.isTopLevel()) {
            topLevelObjects.increment(declaration)
        }
        if (declaration.isCompanion()) {
            companionObjects.increment(declaration)
        }
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        functions.increment(function)
        if (function.annotationEntries.any { it.calleeExpression!!.text == "inline" }) {
            inlineFunctions.increment(function)
        }
        if (function.receiverTypeReference != null) {
            extensionFunctions.increment(function)
            if (PsiTreeUtil.getParentOfType(function, KtClass::class.java) != null) {
                extensionFunctionsInClasses.increment(function)
            }
        }
    }

    override fun visitDelegatedSuperTypeEntry(specifier: KtDelegatedSuperTypeEntry) {
        super.visitDelegatedSuperTypeEntry(specifier)
        delegationBySpecifiers.increment(specifier)
    }

    override fun visitLambdaExpression(expression: KtLambdaExpression) {
        super.visitLambdaExpression(expression)
        lambdas.increment(expression)
        if (expression.hasDeclaredReturnType()) {
            lambdasWithDeclaredReturnType.increment(expression)
        }
    }

    override fun visitLabeledExpression(expression: KtLabeledExpression) {
        super.visitLabeledExpression(expression)
        labeledExpressions.increment(expression)
    }

    override fun visitBreakExpression(expression: KtBreakExpression) {
        super.visitBreakExpression(expression)
        if (expression.getTargetLabel() != null) {
            qualifiedBreakContinue.increment(expression)
        }
    }

    override fun visitContinueExpression(expression: KtContinueExpression) {
        super.visitContinueExpression(expression)
        if (expression.getTargetLabel() != null) {
            qualifiedBreakContinue.increment(expression)
        }
    }

    override fun visitReturnExpression(expression: KtReturnExpression) {
        super.visitReturnExpression(expression)
        if (expression.getTargetLabel() != null) {
            returnWithLabel.increment(expression)
        }
    }

    override fun visitWhileExpression(expression: KtWhileExpression) {
        super.visitWhileExpression(expression)
        whileLoops.increment(expression)
    }

    override fun visitDoWhileExpression(expression: KtDoWhileExpression) {
        super.visitDoWhileExpression(expression)
        doWhileLoops.increment(expression)
    }

    override fun visitWhenExpression(expression: KtWhenExpression) {
        super.visitWhenExpression(expression)
        if (expression.subjectExpression != null) {
            whenWithExpression.increment(expression)
        } else {
            whenWithoutExpression.increment(expression)
        }
    }

    override fun visitWhenConditionInRange(condition: KtWhenConditionInRange) {
        super.visitWhenConditionInRange(condition)
        whenConditionInRange.increment(condition)
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        if (property.isVar) {
            vars.increment(property)
        } else {
            vals.increment(property)
        }
    }

    override fun visitTypeParameter(parameter: KtTypeParameter) {
        super.visitTypeParameter(parameter)
        typeParameters.increment(parameter)
        if (parameter.variance != Variance.INVARIANT) {
            typeParametersWithVariance.increment(parameter)
        }
    }

    override fun visitTypeProjection(typeProjection: KtTypeProjection) {
        super.visitTypeProjection(typeProjection)
        typeArguments.increment(typeProjection)
        when (typeProjection.projectionKind) {
            KtProjectionKind.IN, KtProjectionKind.OUT -> typeArgumentsWithVariance.increment(typeProjection)
            KtProjectionKind.STAR -> typeArgumentsWithStar.increment(typeProjection)
        }
    }

    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        super.visitBinaryExpression(expression)
        if (expression.operationToken == KtTokens.RANGE) {
            rangeOperators.increment(expression)
        }
    }

    override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
        super.visitBinaryWithTypeRHSExpression(expression)
        if (expression.operationReference.text == ":") {
            typesAfterColon.increment(expression)
        }
    }

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        if (element.node.elementType == KtTokens.FIELD_IDENTIFIER) {
            backingFields.increment(element)
        }
    }

    fun report() {
        println("Repositories analyzed: $repositoriesAnalyzed")
        println("Files analyzed: $filesAnalyzed")
        println("Lines analyzed: $linesAnalyzed")
        syntaxErrors.report()
        delegationBySpecifiers.report()
        classes.report()
        companionObjects.report()
        primaryConstructorVisibility.report()
        innerClasses.report()
        innerClassesWithOuterTypeParameters.report()
        objects.report()
        topLevelObjects.report()
        enums.report()
        enumsWithConstructorParameters.report()
        enumsWithEntriesAndMembersMixed.report()
        enumEntries.report()
        enumEntriesWithBody.report()
        functions.report()
        inlineFunctions.report()
        extensionFunctions.report()
        extensionFunctionsInClasses.report()
        lambdas.report()
        lambdasWithDeclaredReturnType.report()
        labeledExpressions.report()
        qualifiedBreakContinue.report()
        returnWithLabel.report()
        whileLoops.report()
        doWhileLoops.report()
        whenWithExpression.report()
        whenWithoutExpression.report()
        whenConditionInRange.report()
        vals.report()
        vars.report()
        typeParameters.report()
        typeParametersWithVariance.report()
        typeArguments.report()
        typeArgumentsWithVariance.report()
        typeArgumentsWithStar.report()
        rangeOperators.report()
        typesAfterColon.report()
        backingFields.report()
    }
}

class CLI {
    @Option(name = "-local", usage = "use local repository versions, do not update from GitHub")
    var local: Boolean = false

    @Option(name = "-stats-only", usage = "don't track individual usages, only their counts")
    var statsOnly: Boolean = false

    @Option(name = "-only-analyze", usage = "only analyze a local corpus, no interaction with GitHub")
    var onlyAnalyze: Boolean = false

    @Option(name = "-max-repo-count", usage = "maximum number of repos to process")
    var maxRepoCount: Int = 1000000

    @Argument
    var corpusDirectoryPath: String? = null
}

fun main(args: Array<String>) {
    val options = CLI()
    with(CmdLineParser(options)) {
        try {
            parseArgument(*args)
        }
        catch (e: CmdLineException) {
            printUsage()
            return
        }
        if (options.corpusDirectoryPath == null) {
            printUsage()
            return
        }
    }

    val ktrawler = Ktrawler(options.statsOnly)
    if (options.onlyAnalyze) {
        ktrawler.analyzeRepository(options.corpusDirectoryPath!!)
    } else {
        val korpus = Korpus(options.corpusDirectoryPath!!)
        var reposProcessed = 0

        val excludedRepos = File("excludedRepos.txt")
        val excludedRepoList = if (excludedRepos.exists())
            excludedRepos.readLines().map { it.trim() }.filter { it.length > 0 }
        else
            listOf()

        korpus.update(options.local) { repoPath ->
            println("Analyzing repository ${repoPath}")
            if (!excludedRepoList.any { repoPath.endsWith("/" + it) }) {
                ktrawler.analyzeRepository(repoPath)
                ++reposProcessed < options.maxRepoCount
            } else {
                true
            }
        }

        val privateRepo = File("privateRepos.txt")
        if (privateRepo.exists()) {
            privateRepo.readLines().forEach {
                if (it.trim().length > 0) {
                    println("Analyzing private repository $it")
                    ktrawler.analyzeRepository(it)
                }
            }
        }
    }

    ktrawler.report()
}

private fun CmdLineParser.printUsage() {
    System.err.println("Usage: ktrawler [options] <corpus-directory-path>")
    printUsage(System.err)
}