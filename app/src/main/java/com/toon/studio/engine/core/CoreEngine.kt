package com.toon.studio.engine.core

/**
 * Central architecture for managing TOON Animation Studio engines.
 * Each engine is registered and managed independently.
 */

enum class EngineId {
    CORE,
    SCENE,
    TIMELINE,
    TWO_D,
    THREE_D,
    CHARACTER,
    ENVIRONMENT,
    ANIMATION,
    PHYSICS,
    CAMERA,
    DIALOGUE,
    LIP_SYNC,
    AUDIO,
    RENDERING,
    EXPORT,
    SUBTITLE,
    COMMAND,
    AI_ASSISTANT
}

enum class EngineState {
    REGISTERED,
    INITIALIZING,
    READY,
    FAILED,
    STOPPED
}

data class EngineContext(
    val appVersion: String,
    val attributes: Map<String, String> = emptyMap()
)

sealed class EngineResult {
    data object Success : EngineResult()
    data class Failure(val reason: String) : EngineResult()
}

/**
 * Contract that every future TOON engine must implement.
 */
interface ToonEngine {
    val id: EngineId

    fun initialize(context: EngineContext): EngineResult

    fun shutdown()
}

/**
 * Keeps track of registered engines and their lifecycle states.
 */
class EngineRegistry {

    private val engines = linkedMapOf<EngineId, ToonEngine>()
    private val states = mutableMapOf<EngineId, EngineState>()

    @Synchronized
    fun register(engine: ToonEngine): Boolean {
        if (engines.containsKey(engine.id)) return false

        engines[engine.id] = engine
        states[engine.id] = EngineState.REGISTERED
        return true
    }

    @Synchronized
    fun initializeAll(context: EngineContext): Map<EngineId, EngineResult> {
        val results = linkedMapOf<EngineId, EngineResult>()

        engines.forEach { (id, engine) ->
            states[id] = EngineState.INITIALIZING

            val result = try {
                engine.initialize(context)
            } catch (exception: Exception) {
                EngineResult.Failure(
                    exception.message ?: "Unknown initialization error"
                )
            }

            states[id] = when (result) {
                is EngineResult.Success -> EngineState.READY
                is EngineResult.Failure -> EngineState.FAILED
            }

            results[id] = result
        }

        return results
    }

    @Synchronized
    fun shutdownAll() {
        engines.entries.toList().asReversed().forEach { (id, engine) ->
            try {
                engine.shutdown()
            } catch (_: Exception) {
                // Continue shutting down the remaining engines.
            }

            states[id] = EngineState.STOPPED
        }
    }

    @Synchronized
    fun getState(id: EngineId): EngineState? = states[id]

    @Synchronized
    fun getEngine(id: EngineId): ToonEngine? = engines[id]

    @Synchronized
    fun registeredEngines(): List<EngineId> = engines.keys.toList()
}

/**
 * Main entry point for the TOON engine architecture.
 */
class CoreEngine(
    private val registry: EngineRegistry = EngineRegistry()
) {

    fun registerEngine(engine: ToonEngine): Boolean {
        return registry.register(engine)
    }

    fun initialize(context: EngineContext): Map<EngineId, EngineResult> {
        return registry.initializeAll(context)
    }

    fun shutdown() {
        registry.shutdownAll()
    }

    fun getEngineState(id: EngineId): EngineState? {
        return registry.getState(id)
    }

    fun getRegisteredEngines(): List<EngineId> {
        return registry.registeredEngines()
    }
}
