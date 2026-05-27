/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftimport

import kotlinx.serialization.json.Json
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import java.util.concurrent.CountDownLatch

internal interface SwiftPMXcodeDumpBuildServiceParameters : BuildServiceParameters {
    val sharedXcodeDumpRoot: DirectoryProperty
    val sharedSyntheticPackageRoot: DirectoryProperty
    val sharedCheckoutDirectoryRoot: DirectoryProperty
}

internal abstract class SwiftPMXcodeDumpBuildService : BuildService<SwiftPMXcodeDumpBuildServiceParameters> {

    /**
     * All mutable service state is guarded by this lock because multiple dump tasks can execute in parallel.
     *
     * The task graph cannot be changed at execution time, so coordination happens by sharing an execution bucket:
     * one task becomes the owner, other matching tasks wait for the owner and then point downstream work at the same
     * shared outputs.
     */
    private val stateLock = Any()

    /** In-memory buckets for the current Gradle invocation, keyed by the xcodebuild execution fingerprint. */
    private val dumpBucketsByExecutionHash = mutableMapOf<XcodeDumpBucketMapKey, XcodeDumpBucket>()
    private val fetchBucketsByPackageHash = mutableMapOf<String, SwiftResolveBucket>()
    private val generatePackageBucketByPackageHash = mutableMapOf<String, GeneratePackageBucket>()


    class XcodeDumpBucket(
        val ownerDumpDir: File,
        val ownerDerivedDataDir: File,
        val completion: CountDownLatch = CountDownLatch(1),
        var failure: Throwable? = null,
        var completed: Boolean = false,
    )

    class SwiftResolveBucket(
        val ownerPackageResolvedFile: File,
        val ownerWorkspaceStateFile : File,
        val ownerSwiftPMDependenciesCheckout: File,
        val ownerSyntheticImportProjectRoot: File,
        val completion: CountDownLatch = CountDownLatch(1),
        var failure: Throwable? = null,
        var completed: Boolean = false,
    )

    class GeneratePackageBucket(
        val ownerSyntheticPackageRoot: File,
        val completion: CountDownLatch = CountDownLatch(1),
        var failure: Throwable? = null,
        var completed: Boolean = false,
    )



    /**
     * Result of trying to acquire a bucket.
     *
     * [Owner] means the caller must run xcodebuild and then mark the bucket as completed/failed.
     * [Existing] means another task or a previous invocation already owns reusable outputs, so the caller waits and
     * writes its local location marker to those shared outputs.
     */
    sealed class XcodeDumpClaim {
        abstract val bucket: XcodeDumpBucket

        data class Owner(override val bucket: XcodeDumpBucket) : XcodeDumpClaim()
        data class Existing(override val bucket: XcodeDumpBucket) : XcodeDumpClaim()
    }


    sealed class SwiftFetchClaim {
        abstract val bucket: SwiftResolveBucket

        data class Owner(override val bucket: SwiftResolveBucket) : SwiftFetchClaim()
        data class Existing(override val bucket: SwiftResolveBucket) : SwiftFetchClaim()
    }

    sealed class GeneratePackageClaim {
        abstract val bucket: GeneratePackageBucket

        data class Owner(override val bucket: GeneratePackageBucket) : GeneratePackageClaim()
        data class Existing(override val bucket: GeneratePackageBucket) : GeneratePackageClaim()
    }

    fun claimOrJoinPackageGeneration(
        packageHash: String,
    ): GeneratePackageClaim {
        synchronized(stateLock) {
            val existingByPackageHash = generatePackageBucketByPackageHash[packageHash]
            if (existingByPackageHash != null) return GeneratePackageClaim.Existing(existingByPackageHash)

            val reusableBucket = findReusablePackageGenerationInSharedRoot(
                packageHash
            )

            if (reusableBucket != null) {
                generatePackageBucketByPackageHash[packageHash] = reusableBucket
                return GeneratePackageClaim.Existing(reusableBucket)
            }

            val newBucket = GeneratePackageBucket(
                ownerSyntheticPackageRoot = sharedPackageGenerationRoot(packageHash)
            )

            generatePackageBucketByPackageHash[packageHash] = newBucket
            return GeneratePackageClaim.Owner(newBucket)
        }
    }

    fun claimOrJoinSwiftResolve(
        packageHash: String,
    ): SwiftFetchClaim {
        synchronized(stateLock) {
            val existingByExecutionHash = fetchBucketsByPackageHash[packageHash]
            if (existingByExecutionHash != null) return SwiftFetchClaim.Existing(existingByExecutionHash)


            val reusableBucket = findReusableFetchBucketInSharedRoot(
                packageHash
            )
            if (reusableBucket != null) {
                fetchBucketsByPackageHash[packageHash] = reusableBucket
                return SwiftFetchClaim.Existing(reusableBucket)
            }

            val packageRoot = sharedPackageGenerationRoot(packageHash)
            val checkoutDir = sharedCheckoutDir(packageHash)
            val newBucket = SwiftResolveBucket(
                ownerPackageResolvedFile = sharedPackageResolved(packageRoot),
                ownerWorkspaceStateFile = sharedCheckoutWorkspaceStateJsonFile(checkoutDir),
                ownerSwiftPMDependenciesCheckout = checkoutDir,
                ownerSyntheticImportProjectRoot = packageRoot,
            )



            fetchBucketsByPackageHash[packageHash] = newBucket
            return SwiftFetchClaim.Owner(newBucket)
        }
    }

    fun claimOrJoinXcodeDump(
        xcodebuildExecutionHash: String,
        xcodebuildSdk: String,
    ): XcodeDumpClaim {
        synchronized(stateLock) {
            val executionKey = XcodeDumpBucketMapKey(xcodebuildExecutionHash, xcodebuildSdk)
            val existingByExecutionHash = dumpBucketsByExecutionHash[executionKey]
            if (existingByExecutionHash != null) return XcodeDumpClaim.Existing(existingByExecutionHash)

            val reusableBucket = findReusableDumpBucketInSharedRoot(
                xcodebuildExecutionHash = xcodebuildExecutionHash,
                xcodebuildSdk = xcodebuildSdk,
                sdkDerivedDataDirName = "dd_$xcodebuildSdk",
            )
            if (reusableBucket != null) {
                dumpBucketsByExecutionHash[executionKey] = reusableBucket
                return XcodeDumpClaim.Existing(reusableBucket)
            }

            // No reusable owner exists. The current task becomes the owner and will run xcodebuild into the root-build
            // bucket. The owner still builds its own synthetic package and uses its own SwiftPM checkout; only the dump
            // and DerivedData output locations are shared.
            val bucketId = xcodebuildExecutionHash
            val bucketRoot = sharedDumpBucketRoot(bucketId)
            val newBucket = XcodeDumpBucket(
                ownerDumpDir = sharedDumpDir(bucketRoot, xcodebuildSdk),
                ownerDerivedDataDir = sharedDerivedDataDir(bucketRoot),
            )
            dumpBucketsByExecutionHash[executionKey] = newBucket
            return XcodeDumpClaim.Owner(newBucket)
        }
    }

    private fun sharedDumpBucketRoot(bucketId: String): File =
        parameters.sharedXcodeDumpRoot.get().asFile.resolve(bucketId)

    private fun sharedDumpDir(bucketRoot: File, xcodebuildSdk: String): File =
        bucketRoot.resolve("swiftImportClangDump/$xcodebuildSdk")

    private fun sharedDerivedDataDir(bucketRoot: File): File =
        bucketRoot.resolve("swiftImportDd")

    private fun sharedPackageGenerationRoot(packageHash: String): File =
        parameters.sharedSyntheticPackageRoot.get().asFile.resolve(packageHash)

    private fun sharedCheckoutDir(packageHash: String): File =
        parameters.sharedCheckoutDirectoryRoot.get().asFile.resolve(packageHash)

    private fun sharedCheckoutWorkspaceStateJsonFile(checkoutDir: File): File =
        checkoutDir.resolve("workspace-state.json")

    private fun sharedPackageResolved(packageDir: File): File =
        packageDir.resolve("Package.resolved")

    fun awaitPackageGeneration(bucket : GeneratePackageBucket){
        bucket.completion.await()
        bucket.failure?.let {
            throw GradleException("Shared SwiftPM package generation failed for bucket '${bucket}'", it)

        }
    }

    fun markPackageGenerationCompleted(bucket : GeneratePackageBucket) {
        synchronized(stateLock){
            bucket.completed = true
            bucket.completion.countDown()
        }
    }

    fun markPackageGenerationFailed(bucket: GeneratePackageBucket, failure: Throwable) {
        synchronized(stateLock) {
            bucket.failure = failure
            bucket.completion.countDown()
        }
    }

    fun awaitXcodeDump(bucket: XcodeDumpBucket) {
        // Joined tasks wait here instead of depending on an owner task. At execution time the Gradle task graph is already
        // fixed, so a latch inside the build service is the safe coordination primitive.
        bucket.completion.await()
        bucket.failure?.let {
            throw GradleException("Shared SwiftPM xcodebuild dump failed for bucket '${bucket}'", it)
        }
    }

    fun markXcodeDumpCompleted(bucket: XcodeDumpBucket) {
        synchronized(stateLock) {
            // The stamp protects root-build directory reuse: existing files alone are not enough because a later dump
            // can overwrite the same deterministic bucket directory with a different fingerprint.
            bucket.completed = true
            bucket.completion.countDown()
        }
    }

    fun markXcodeDumpFailed(bucket: XcodeDumpBucket, failure: Throwable) {
        synchronized(stateLock) {
            bucket.failure = failure
            bucket.completion.countDown()
        }
    }

    fun markSwiftResolveCompleted(bucket: SwiftResolveBucket) {
        synchronized(stateLock) {
            bucket.completed = true
            bucket.completion.countDown()
        }
    }

    fun markSwiftResolveFailed(bucket: SwiftResolveBucket, failure: Throwable) {
        synchronized(stateLock) {
            bucket.failure = failure
            bucket.completion.countDown()
        }
    }

    fun awaitSwiftResolved(bucket: SwiftResolveBucket) {
        // Joined tasks wait here instead of depending on an owner task. At execution time the Gradle task graph is already
        // fixed, so a latch inside the build service is the safe coordination primitive.
        bucket.completion.await()
        bucket.failure?.let {
            throw GradleException("Shared SwiftPM xcodebuild dump failed for bucket '${bucket}'", it)
        }
    }

    fun findSwiftResolveBucket(packageHash: String): SwiftResolveBucket? =
        synchronized(stateLock) {
            fetchBucketsByPackageHash[packageHash]
                ?: findReusableFetchBucketInSharedRoot(packageHash)?.also {
                    fetchBucketsByPackageHash[packageHash] = it
                }
        }

    fun findPackageGenerationBucket(packageHash: String): GeneratePackageBucket? =
        synchronized(stateLock) {
            generatePackageBucketByPackageHash[packageHash]
                ?: findReusablePackageGenerationInSharedRoot(packageHash)?.also {
                    generatePackageBucketByPackageHash[packageHash] = it
                }
        }

    //TODO add findReusablePackageGenerationInSharedRoot
    private fun findReusablePackageGenerationInSharedRoot(
        packageHash: String,
    ): GeneratePackageBucket? {
        val syntheticPackageRoot = sharedPackageGenerationRoot(packageHash)
        val expectedPackageManifest = syntheticPackageRoot.resolve("Package.swift")

        if (!expectedPackageManifest.exists()) return null

        return GeneratePackageBucket(
            ownerSyntheticPackageRoot = syntheticPackageRoot,
            completion = CountDownLatch(0),
            completed = true,
        )

    }

    private fun findReusableFetchBucketInSharedRoot(
        packageHash: String,
    ): SwiftResolveBucket? {
        val syntheticPackageRoot = sharedPackageGenerationRoot(packageHash)
        val checkoutDir = sharedCheckoutDir(packageHash)
        val packageResolved = sharedPackageResolved(syntheticPackageRoot)
        val workspaceState = sharedCheckoutWorkspaceStateJsonFile(checkoutDir)

        if (!workspaceState.exists()) return null

        return SwiftResolveBucket(
            packageResolved,
            workspaceState,
            checkoutDir,
            syntheticPackageRoot,
            completion = CountDownLatch(0),
            completed = true,
        )
    }

    private fun findReusableDumpBucketInSharedRoot(
        xcodebuildExecutionHash: String,
        xcodebuildSdk: String,
        sdkDerivedDataDirName: String,
    ): XcodeDumpBucket? {
        val bucketRoot = sharedDumpBucketRoot(xcodebuildExecutionHash)
        val ownerDumpDir = sharedDumpDir(bucketRoot, xcodebuildSdk)
        val ownerDerivedDataDir = sharedDerivedDataDir(bucketRoot)

        // A root-build bucket is reusable only if both logical outputs still exist: dumped clang/ld args and the SDK
        // DerivedData directory that contains the products referenced by those args.
        if (!ownerDumpDir.resolve("clang_args_dump").isDirectory) return null
        if (!ownerDumpDir.resolve("ld_args_dump").isDirectory) return null
        if (!ownerDerivedDataDir.resolve(sdkDerivedDataDirName).exists()) return null

        return XcodeDumpBucket(
            ownerDumpDir = ownerDumpDir,
            ownerDerivedDataDir = ownerDerivedDataDir,
            // Root-build buckets discovered from disk are already complete. Joined tasks can pass through awaitXcodeDump
            // immediately and then write their local location marker from the restored owner directories.
            completion = CountDownLatch(0),
            completed = true,
        )
    }


    companion object {
        private const val SERVICE_NAME = "swiftPMXcodeDumpBuildService"

        /**
         * Registers the shared service once per build.
         */
        fun registerIfAbsent(
            project: Project,
            xcodeDumpsDir: Provider<Directory>,
            checkoutDir: Provider<Directory>,
            generatePackageDir: Provider<Directory>,
        ): Provider<SwiftPMXcodeDumpBuildService> =
            project.gradle.sharedServices.registerIfAbsent(
                SERVICE_NAME,
                SwiftPMXcodeDumpBuildService::class.java
            ) { buildServiceSpec ->
                buildServiceSpec.parameters.sharedXcodeDumpRoot.set(
                    xcodeDumpsDir
                )
                buildServiceSpec.parameters.sharedSyntheticPackageRoot.set(
                    generatePackageDir
                )
                buildServiceSpec.parameters.sharedCheckoutDirectoryRoot.set(
                    checkoutDir
                )
            }
    }
}

private data class XcodeDumpBucketMapKey(
    val xcodebuildExecutionHash: String,
    val xcodebuildSdk: String,
)


internal val dumpTaskFingerprintJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
