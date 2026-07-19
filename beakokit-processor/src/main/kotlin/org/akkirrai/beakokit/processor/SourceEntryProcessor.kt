package org.akkirrai.beakokit.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate

internal class SourceEntryProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        val symbols = resolver.getSymbolsWithAnnotation(SOURCE_ENTRY_ANNOTATION).toList()
        val deferred = symbols.filterNot(KSAnnotated::validate)
        if (deferred.isNotEmpty()) return deferred

        val sources = symbols.filterIsInstance<KSClassDeclaration>()
        if (sources.size != symbols.size) {
            symbols.filterNot { it is KSClassDeclaration }
                .forEach { logger.error("@SourceEntry can only annotate classes", it) }
            return emptyList()
        }

        var hasErrors = false
        val ids = mutableSetOf<String>()
        val orders = mutableSetOf<Int>()
        sources.forEach { source ->
            val id = source.sourceId()
            val order = source.sourceOrder()
            if (id == null || !SOURCE_ID_PATTERN.matches(id)) {
                logger.error("@SourceEntry id must be a lowercase slug", source)
                hasErrors = true
            } else if (!ids.add(id)) {
                logger.error("Duplicate @SourceEntry id: $id", source)
                hasErrors = true
            }
            if (order == null || order < 0) {
                logger.error("@SourceEntry order must be zero or greater", source)
                hasErrors = true
            } else if (!orders.add(order)) {
                logger.error("Duplicate @SourceEntry order: $order", source)
                hasErrors = true
            }
            if (source.getAllSuperTypes().none { it.declaration.qualifiedName?.asString() == ANIME_SOURCE_TYPE }) {
                logger.error("@SourceEntry class must implement AnimeSource", source)
                hasErrors = true
            }
            val parameters = source.primaryConstructor?.parameters.orEmpty()
            if (parameters.size != 1 || parameters.single().type.resolve().declaration.qualifiedName?.asString() != SOURCE_CONTEXT_TYPE) {
                logger.error("@SourceEntry class must have exactly one SourceContext constructor parameter", source)
                hasErrors = true
            }
            if (source.modifiers.any { it == Modifier.PRIVATE || it == Modifier.PROTECTED || it == Modifier.INTERNAL }) {
                logger.error("@SourceEntry class must be public", source)
                hasErrors = true
            }
            val infoProperty = source.declarations
                .filterIsInstance<KSClassDeclaration>()
                .firstOrNull(KSClassDeclaration::isCompanionObject)
                ?.declarations
                ?.filterIsInstance<KSPropertyDeclaration>()
                ?.firstOrNull { it.simpleName.asString() == "INFO" }
            if (infoProperty == null) {
                logger.error("@SourceEntry class must expose companion object INFO", source)
                hasErrors = true
            }
        }
        if (hasErrors) return emptyList()

        generateCatalog(
            sources.sortedWith(
                compareBy<KSClassDeclaration> { it.sourceOrder() }
                    .thenBy { it.qualifiedName?.asString() },
            ),
        )
        generated = true
        return emptyList()
    }

    private fun generateCatalog(sources: List<KSClassDeclaration>) {
        val files = sources.mapNotNull(KSClassDeclaration::containingFile).toTypedArray()
        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = true, *files),
            packageName = GENERATED_PACKAGE,
            fileName = GENERATED_OBJECT,
        ).bufferedWriter().use { writer ->
            writer.appendLine("package $GENERATED_PACKAGE")
            writer.appendLine()
            writer.appendLine("internal object $GENERATED_OBJECT {")
            sources.forEach { source ->
                val id = source.sourceId()!!
                val constantName = id.uppercase().replace('-', '_') + "_ID"
                writer.appendLine("    val $constantName = org.akkirrai.beakokit.api.SourceId(\"$id\")")
            }
            writer.appendLine()
            writer.appendLine("    val catalog = org.akkirrai.beakokit.api.SourceCatalog(")
            writer.appendLine("        listOf(")
            sources.forEach { source ->
                val type = source.qualifiedName!!.asString()
                writer.appendLine("            org.akkirrai.beakokit.api.SourceCatalogEntry(")
                writer.appendLine("                info = $type.INFO,")
                writer.appendLine("                factory = org.akkirrai.beakokit.api.SourceFactory { context -> $type(context) },")
                writer.appendLine("            ),")
            }
            writer.appendLine("        ),")
            writer.appendLine("    )")
            writer.appendLine("}")
        }
    }

    private fun KSClassDeclaration.sourceId(): String? = sourceEntryArgument("id") as? String

    private fun KSClassDeclaration.sourceOrder(): Int? = sourceEntryArgument("order") as? Int

    private fun KSClassDeclaration.sourceEntryArgument(name: String): Any? = annotations
        .firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == SOURCE_ENTRY_ANNOTATION }
        ?.arguments
        ?.firstOrNull { it.name?.asString() == name }
        ?.value

    private companion object {
        const val SOURCE_ENTRY_ANNOTATION = "org.akkirrai.beakokit.api.SourceEntry"
        const val ANIME_SOURCE_TYPE = "org.akkirrai.beakokit.api.AnimeSource"
        const val SOURCE_CONTEXT_TYPE = "org.akkirrai.beakokit.api.SourceContext"
        const val GENERATED_PACKAGE = "org.akkirrai.beakokit.generated"
        const val GENERATED_OBJECT = "GeneratedSourceCatalog"
        val SOURCE_ID_PATTERN = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    }
}
