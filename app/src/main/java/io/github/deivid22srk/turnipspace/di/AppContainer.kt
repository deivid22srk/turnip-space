package io.github.deivid22srk.turnipspace.di

import android.content.Context
import io.github.deivid22srk.turnipspace.common.SettingsStore
import io.github.deivid22srk.turnipspace.data.driver.DriverRepository
import io.github.deivid22srk.turnipspace.data.gpu.GpuDetector
import io.github.deivid22srk.turnipspace.data.shizuku.ShizukuManager
import io.github.deivid22srk.turnipspace.data.space.ApkInstaller
import io.github.deivid22srk.turnipspace.data.space.SpaceRepository
import io.github.deivid22srk.turnipspace.virtual.VirtualEngine

/** Hand-rolled dependency container (no framework — keeps the app lean). */
class AppContainer(context: Context) {

    val settings = SettingsStore(context)
    val spaceRepository = SpaceRepository(context)
    val driverRepository = DriverRepository(context)
    val apkInstaller = ApkInstaller(context, spaceRepository)
    val gpuDetector = GpuDetector()
    val shizukuManager = ShizukuManager(context)
    val virtualEngine = VirtualEngine(spaceRepository, driverRepository)
}
