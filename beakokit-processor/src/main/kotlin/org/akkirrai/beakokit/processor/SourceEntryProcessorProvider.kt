package org.akkirrai.beakokit.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class SourceEntryProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        SourceEntryProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
}
