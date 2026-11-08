package com.toon.studio.engine.core

import java.util.Collections
import java.util.EnumSet

/**
 * Identifiers for the engines that make up TOON Animation Studio.
 *
 * CORE is reserved for [CoreEngine] itself and must not be registered as a
 * child engine. All other engines are registered independently.
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

/** Current lifecycle state of a registered engine. */
enum class EngineState {
    REGISTERED,
    INITIALIZING,
    READY,
    FAILED,
    STOPPED
}

/**
 * The supported character forms for the creative world.
 *
 * This deliberately describes only organic, natural character categories.
 * Future character and rendering engines must use this shared policy.
 */
enum class CharacterForm {
    HUMAN,
    ANIMAL,
    ORGANIC_FANTASY,
    LIVING_PLANT
}

/**
 * Global creative constraints supplied to every engine at initialization.
 *
 * This is a shared contract, not a renderer by itself. Character, environment,
 * animation and rendering engines must read and honour it when they are built.
 */
object CreativeWorldPolicy {
    val allowedCharacterForms: Set<CharacterForm> =
        Collections.unmodifiableSet(
            EnumSet.of(
                CharacterForm.HUMAN,
                CharacterForm.ANIMAL,
                CharacterForm.ORGANIC_FANTASY,
                CharacterForm.LIVING_PLANT
            )
        )

    const val requireNaturalMovement: Boolean = true
    const val requireNaturalEnvironments: Boolean = true
    const val preserveCharacterIdentityAcrossScenes: Boolean = true
    const val useOneSharedScenePlanForManualAndCommandModes: Boolean = true
}

/**
 * Shared configuration passed to each engine.
 *
 * [attributes] is reserved for non-secret engine configuration. Do not place
 * passwords, API keys or other credentials in this map.
 */
data class EngineContext(
    val appVersion: String,
    val attributes: Map<String, String> = emptyMap(),
    val worldPolicy: CreativeWorldPolicy = CreativeWorldPolicy
)

/** A truthful result from an engine lifecycle operation. */
sealed class EngineResult {
    object Success : EngineResult()

    data class Failure(
        val reason: String,
        val code: String? = null
    ) : EngineResult()
}

/**
 * Contract implemented by every engine registered with [CoreEngine].
 *
 * Dependencies are initialized before the dependent engine. Keep dependencies
 * limited to actual engine requirements to avoid circular dependencies.
 */
interface ToonEngine {
    val id: EngineId

    val dependencies: Set<EngineId>
        get() = emptySet()

    fun initialize(context: EngineContext): EngineResult

    fun shutdown()
}

/** Read-only status information for one registered engine. */
data class EngineStatus(
    val id: EngineId,
    val state: EngineState,
    val dependencies: Set<EngineId>,
    val failureReason: String? = null
)

/**
 * Owns engine registration and lifecycle management.
 *
 * The registry:
 * - prevents duplicate registrations;
 * - initializes dependencies before dependent engines;
 * - detects missing dependencies and dependency cycles;
 * - records failures instead of pretending an engine is ready;
 * - shuts down attempted engines in reverse initialization order;
 * - does not depend on Android, Compose, rendering libraries or coroutines.
 *
 * Lifecycle methods are synchronized because engine initialization and
 * shutdown must not run concurrently against the same registry.
 */
class EngineRegistry {

    private val engines = linkedMapOf<EngineId, ToonEngine>()
    private val states = linkedMapOf<EngineId, EngineState>()
    private val failures = linkedMapOf<EngineId, String>()
    private val initializationOrder = arrayListOf<EngineId>()

    private var lifecycleOperationInProgress: Boolean = false

    /**
     * Registers one engine.
     *
     * Returns false when the engine ID is CORE or has already been registered.
     */
    @Synchronized
    fun register(engine: ToonEngine): Boolean {
        if (engine.id == EngineId.CORE) return false
        if (engines.containsKey(engine.id)) return false

        engines[engine.id] = engine
        states[engine.id] = EngineState.REGISTERED
        failures.remove(engine.id)
        return true
    }

    /**
     * Initializes every registered engine in dependency order.
     *
     * Engines already in READY state are not initialized a second time.
     * A dependent engine is skipped when one of its dependencies is missing
     * or has failed initialization.
     */
    @Synchronized
    fun initializeAll(context: EngineContext): Map<EngineId, EngineResult> {
        if (lifecycleOperationInProgress) {
            return engines.keys.associateWith {
                EngineResult.Failure(
                    reason = "Another engine lifecycle operation is already running.",
                    code = "LIFECYCLE_BUSY"
                )
            }
        }

        lifecycleOperationInProgress = true
        try {
            val results = linkedMapOf<EngineId, EngineResult>()
            val dependencyProblems = linkedMapOf<EngineId, String>()
            val initializationOrderForThisRun =
                resolveInitializationOrder(dependencyProblems)

            dependencyProblems.forEach { (id, reason) ->
                if (states[id] != EngineState.READY) {
                    states[id] = EngineState.FAILED
                }
                failures[id] = reason
                results[id] = EngineResult.Failure(
                    reason = reason,
                    code = "ENGINE_DEPENDENCY"
                )
            }

            for (id in initializationOrderForThisRun) {
                val engine = engines[id] ?: continue

                if (states[id] == EngineState.READY) {
                    results[id] = EngineResult.Success
                    continue
                }

                val unavailableDependency = engine.dependencies
                    .sortedBy { it.name }
                    .firstOrNull { dependency ->
                        states[dependency] != EngineState.READY
                    }

                if (unavailableDependency != null) {
                    val reason =
                        "Required engine $unavailableDependency is not READY."
                    states[id] = EngineState.FAILED
                    failures[id] = reason
                    results[id] = EngineResult.Failure(
                        reason = reason,
                        code = "ENGINE_DEPENDENCY_NOT_READY"
                    )
                    continue
                }

                states[id] = EngineState.INITIALIZING

                // Track the attempt before calling user engine code. If an
                // engine partially allocates resources and then fails, the
                // shutdown pass can still ask it to clean up.
                if (!initializationOrder.contains(id)) {
                    initializationOrder.add(id)
                }

                val result = try {
                    engine.initialize(context)
                } catch (exception: Exception) {
                    EngineResult.Failure(
                        reason = exception.message
                            ?: exception::class.java.simpleName.ifBlank {
                                "Unknown initialization error"
                            },
                        code = "ENGINE_INITIALIZATION_EXCEPTION"
                    )
                }

                when (result) {
                    is EngineResult.Success -> {
                        states[id] = EngineState.READY
                        failures.remove(id)
                    }

                    is EngineResult.Failure -> {
                        states[id] = EngineState.FAILED
                        failures[id] = result.reason
                    }
                }

                results[id] = result
            }

            // Include a result for every registered engine, even if a future
            // change adds a state that was not covered by dependency ordering.
            engines.keys.forEach { id ->
                if (!results.containsKey(id)) {
                    val reason = "Engine could not be placed in initialization order."
                    states[id] = EngineState.FAILED
                    failures[id] = reason
                    results[id] = EngineResult.Failure(
                        reason = reason,
                        code = "ENGINE_ORDER_UNRESOLVED"
                    )
                }
            }

            return results
        } finally {
            lifecycleOperationInProgress = false
        }
    }

    /**
     * Shuts down engines in reverse order of initialization attempts.
     * One shutdown failure does not prevent the remaining engines from closing.
     */
    @Synchronized
    fun shutdownAll() {
        if (lifecycleOperationInProgress) return

        lifecycleOperationInProgress = true
        try {
            val successfullyStopped = linkedSetOf<EngineId>()

            initializationOrder.toList().asReversed().forEach { id ->
                val engine = engines[id] ?: run {
                    successfullyStopped.add(id)
                    return@forEach
                }

                try {
                    engine.shutdown()
                    states[id] = EngineState.STOPPED
                    failures.remove(id)
                    successfullyStopped.add(id)
                } catch (exception: Exception) {
                    states[id] = EngineState.FAILED
                    failures[id] = exception.message
                        ?: exception::class.java.simpleName.ifBlank {
                            "Unknown shutdown error"
                        }
                }
            }

            initializationOrder.removeAll(successfullyStopped)
        } finally {
            lifecycleOperationInProgress = false
        }
    }

    /** Returns the current state, or null when the ID is not registered. */
    @Synchronized
    fun getState(id: EngineId): EngineState? = states[id]

    /** Returns the registered engine instance, or null when absent. */
    @Synchronized
    fun getEngine(id: EngineId): ToonEngine? = engines[id]

    /** Returns the latest recorded failure for an engine, if any. */
    @Synchronized
    fun getFailureReason(id: EngineId): String? = failures[id]

    /** Returns engine IDs in their original registration order. */
    @Synchronized
    fun registeredEngines(): List<EngineId> = engines.keys.toList()

    /** Returns a status snapshot for the UI and diagnostics. */
    @Synchronized
    fun statusSnapshot(): List<EngineStatus> =
        engines.map { (id, engine) ->
            EngineStatus(
                id = id,
                state = states[id] ?: EngineState.REGISTERED,
                dependencies = engine.dependencies.toSet(),
                failureReason = failures[id]
            )
        }

    /** True only when the requested engine completed initialization. */
    @Synchronized
    fun isReady(id: EngineId): Boolean = states[id] == EngineState.READY

    /** True when at least one engine is registered and all are READY. */
    @Synchronized
    fun areAllRegisteredEnginesReady(): Boolean =
        engines.isNotEmpty() && engines.keys.all { states[it] == EngineState.READY }

    /**
     * Builds a deterministic topological order and reports invalid dependency
     * graphs. Registration order is retained where dependencies allow it.
     */
    private fun resolveInitializationOrder(
        problems: MutableMap<EngineId, String>
    ): List<EngineId> {
        val ordered = arrayListOf<EngineId>()
        val visited = hashSetOf<EngineId>()
        val visiting = arrayListOf<EngineId>()

        fun visit(id: EngineId): Boolean {
            if (visited.contains(id)) return true
            if (problems.containsKey(id)) return false

            val cycleStart = visiting.indexOf(id)
            if (cycleStart >= 0) {
                val cyclePath = (
                    visiting.subList(cycleStart, visiting.size) + id
                ).joinToString(" -> ") { it.name }

                for (cycleId in visiting.subList(cycleStart, visiting.size)) {
                    problems[cycleId] = "Engine dependency cycle detected: $cyclePath."
                }
                return false
            }

            val engine = engines[id] ?: return false
            visiting.add(id)

            var valid = true
            engine.dependencies.sortedBy { it.name }.forEach { dependency ->
                when {
                    dependency == EngineId.CORE -> {
                        problems.putIfAbsent(
                            id,
                            "CORE is the coordinator and cannot be a child-engine dependency."
                        )
                        valid = false
                    }

                    !engines.containsKey(dependency) -> {
                        problems.putIfAbsent(
                            id,
                            "Required engine $dependency has not been registered."
                        )
                        valid = false
                    }

                    !visit(dependency) -> {
                        problems.putIfAbsent(
                            id,
                            "Required engine $dependency has an unresolved dependency."
                        )
                        valid = false
                    }
                }
            }

            visiting.removeAt(visiting.lastIndex)

            return if (valid) {
                visited.add(id)
                ordered.add(id)
                true
            } else {
                problems.putIfAbsent(id, "One or more engine dependencies are invalid.")
                false
            }
        }

        engines.keys.forEach { id -> visit(id) }
        return ordered
    }
}

/**
 * Public entry point for the TOON engine architecture.
 *
 * Keep this class focused on coordination. Scene creation, character acting,
 * camera movement, audio, rendering and export belong to their own engines.
 */
class CoreEngine(
    private val registry: EngineRegistry = EngineRegistry()
) {

    fun registerEngine(engine: ToonEngine): Boolean =
        registry.register(engine)

    fun initialize(context: EngineContext): Map<EngineId, EngineResult> =
        registry.initializeAll(context)

    fun shutdown() {
        registry.shutdownAll()
    }

    fun getEngineState(id: EngineId): EngineState? =
        registry.getState(id)

    fun getEngine(id: EngineId): ToonEngine? =
        registry.getEngine(id)

    fun getFailureReason(id: EngineId): String? =
        registry.getFailureReason(id)

    fun getRegisteredEngines(): List<EngineId> =
        registry.registeredEngines()

    fun getEngineStatuses(): List<EngineStatus> =
        registry.statusSnapshot()

    fun isEngineReady(id: EngineId): Boolean =
        registry.isReady(id)

    fun areAllEnginesReady(): Boolean =
        registry.areAllRegisteredEnginesReady()
}
