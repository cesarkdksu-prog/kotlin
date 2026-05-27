/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

package org.jetbrains.kotlin.gradle.apple

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftimport.PrepareGenerateSyntheticPackageFingerprint
import org.jetbrains.kotlin.gradle.testbase.GradleTest
import org.jetbrains.kotlin.gradle.testbase.KGPBaseTest
import org.jetbrains.kotlin.gradle.testbase.OsCondition
import org.jetbrains.kotlin.gradle.testbase.SwiftPMImportGradlePluginTests
import org.jetbrains.kotlin.gradle.testbase.assertTasksExecuted
import org.jetbrains.kotlin.gradle.testbase.build
import org.jetbrains.kotlin.gradle.testbase.project
import org.jetbrains.kotlin.gradle.uklibs.include
import org.junit.jupiter.api.condition.OS
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OsCondition(
    supportedOn = [OS.MAC],
    enabledOnCI = [OS.MAC],
)
@SwiftPMImportGradlePluginTests
class PrepareGenerateSyntheticPackageFingerprintTests : KGPBaseTest() {


    @GradleTest
    fun `fingerprint task generates same fingerprint given the two target with same flattened dependency graph`(version: GradleVersion) {
        val subProjectName = "subProject"
        project("empty", version) {
            withLockFileFixture {
                val swiftPmPackage = repoRef("Maps").also { createRepo(it.name, listOf("1.0.0")) }

                initSwiftPmProject(cacheDirFile) {
                    sourceSets.appleMain.dependencies {
                        api(project(":$subProjectName"))
                    }
                }

                val subProject = project("empty", version) {
                    initSwiftPmProject(cacheDirFile) {
                        swiftPMDependencies {
                            swiftPackage(
                                url = url(swiftPmPackage.url),
                                version = exact("1.0.0"),
                                products = listOf(product(swiftPmPackage.name))
                            )
                        }
                    }
                }


                include(subProject, subProjectName)

                val prepareFingerPrint = PrepareGenerateSyntheticPackageFingerprint.TASK_NAME

                build(
                    ":$prepareFingerPrint",
                    ":$subProjectName:$prepareFingerPrint",
                ) {

                    assertTasksExecuted(
                        ":$prepareFingerPrint",
                        ":$subProjectName:$prepareFingerPrint",
                    )

                    assertEquals(
                        subProject.projectPath.resolve("build/kotlin/syntheticPackageHash")
                            .readText()
                            .trim(),
                        projectPath.resolve("build/kotlin/syntheticPackageHash")
                            .readText()
                            .trim(),
                        "Projects with same flattened dependency graphs and same build settings should have same fingerprint"
                    )
                }

            }
        }
    }

    @GradleTest
    fun `fingerprint task generates different fingerprint given the two target with different flattened dependency graph`(version: GradleVersion) {
        val subProjectName = "subProject"
        project("empty", version) {
            withLockFileFixture {
                val mapsPackage = repoRef("Maps").also { createRepo(it.name, listOf("1.0.0")) }
                val crpytoPackage = repoRef("Crypto").also { createRepo(it.name, listOf("1.0.0")) }

                initSwiftPmProject(cacheDirFile) {
                    swiftPMDependencies {
                        swiftPackage(
                            url = url(crpytoPackage.url),
                            version = exact("1.0.0"),
                            products = listOf(product(crpytoPackage.name))
                        )
                    }
                    sourceSets.appleMain.dependencies {
                        api(project(":$subProjectName"))
                    }
                }

                val subProject = project("empty", version) {
                    initSwiftPmProject(cacheDirFile) {
                        swiftPMDependencies {
                            swiftPackage(
                                url = url(mapsPackage.url),
                                version = exact("1.0.0"),
                                products = listOf(product(mapsPackage.name))
                            )
                        }
                    }
                }


                include(subProject, subProjectName)

                val prepareFingerPrint = PrepareGenerateSyntheticPackageFingerprint.TASK_NAME

                build(
                    ":$prepareFingerPrint",
                    ":$subProjectName:$prepareFingerPrint",
                ) {

                    assertTasksExecuted(
                        ":$prepareFingerPrint",
                        ":$subProjectName:$prepareFingerPrint",
                    )

                    assertNotEquals(
                        subProject.projectPath.resolve("build/kotlin/syntheticPackageHash")
                            .readText()
                            .trim(),
                        projectPath.resolve("build/kotlin/syntheticPackageHash")
                            .readText()
                            .trim(),
                        "Projects with different flattened dependency graphs should have different fingerprint"
                    )
                }

            }
        }
    }
}
