package io.github.deivid22srk.turnipspace.domain

import android.content.Context
import android.net.Uri
import io.github.deivid22srk.turnipspace.data.driver.DriverRepository
import io.github.deivid22srk.turnipspace.data.gpu.GpuDetector
import io.github.deivid22srk.turnipspace.data.gpu.GpuInfo
import io.github.deivid22srk.turnipspace.data.shizuku.ShizukuManager
import io.github.deivid22srk.turnipspace.data.space.ApkInstaller
import io.github.deivid22srk.turnipspace.data.space.SpaceRepository
import io.github.deivid22srk.turnipspace.di.AppContainer
import io.github.deivid22srk.turnipspace.domain.ApkInstallResult
import io.github.deivid22srk.turnipspace.domain.DriverImportResult
import io.github.deivid22srk.turnipspace.domain.LaunchResult
import io.github.deivid22srk.turnipspace.domain.VirtualSpace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Use-case layer: thin, testable orchestration of repositories/engines.
 * ViewModels depend on these instead of repositories directly.
 */
class CreateSpaceUseCase(private val repo: SpaceRepository) {
    suspend operator fun invoke(name: String): VirtualSpace = repo.create(name.trim())
}

class ListSpacesUseCase(private val repo: SpaceRepository) {
    operator fun invoke(): List<VirtualSpace> = repo.list()
}

class DeleteSpaceUseCase(private val repo: SpaceRepository) {
    suspend operator fun invoke(id: String) = repo.delete(id)
}

class InstallApkUseCase(private val installer: ApkInstaller, private val repo: SpaceRepository) {
    suspend operator fun invoke(spaceId: String, apkUri: Uri): ApkInstallResult {
        val result = installer.install(spaceId, apkUri)
        if (result is ApkInstallResult.Success) {
            repo.get(spaceId)?.let { space ->
                repo.update(space.copy(installedApp = result.app))
            }
        }
        return result
    }
}

class ImportDriverUseCase(private val repo: DriverRepository) {
    suspend operator fun invoke(zipUri: Uri): DriverImportResult = repo.import(zipUri)
}

class DeleteDriverUseCase(
    private val drivers: DriverRepository,
    private val spaces: SpaceRepository,
) {
    suspend operator fun invoke(driverId: String) {
        drivers.delete(driverId)
        // Detach from any space that had it selected (never leave dangling ids).
        spaces.list().filter { it.selectedDriverId == driverId }.forEach {
            spaces.update(it.copy(selectedDriverId = null))
        }
    }
}

class SelectDriverForSpaceUseCase(
    private val spaces: SpaceRepository,
    private val drivers: DriverRepository,
) {
    suspend operator fun invoke(spaceId: String, driverId: String?) {
        val space = spaces.get(spaceId) ?: return
        if (driverId != null && drivers.get(driverId) == null) return
        spaces.update(space.copy(selectedDriverId = driverId))
    }
}

class LaunchSpaceUseCase(
    private val engine: io.github.deivid22srk.turnipspace.virtual.VirtualEngine,
    private val spaces: SpaceRepository,
) {
    suspend operator fun invoke(context: Context, spaceId: String): LaunchResult {
        val space = spaces.get(spaceId)
            ?: return LaunchResult.Failure("error_space_not_found", spaceId)
        return engine.launch(context, space)
    }
}

class ObserveGpuUseCase(private val detector: GpuDetector) {
    operator fun invoke(): Flow<GpuInfo> = flow { emit(detector.detect()) }
}

class ShizukuStatusUseCase(private val manager: ShizukuManager) {
    fun state(): ShizukuManager.State = manager.state()

    fun request() = manager.requestPermission()

    fun enrichGpu(command: String): String? = manager.runCommand(command)
}

/** Factory helper so ViewModels can grab use cases from the container. */
object UseCases {
    fun from(container: AppContainer): Set {
        return Set(
            createSpace = CreateSpaceUseCase(container.spaceRepository),
            listSpaces = ListSpacesUseCase(container.spaceRepository),
            deleteSpace = DeleteSpaceUseCase(container.spaceRepository),
            installApk = InstallApkUseCase(container.apkInstaller, container.spaceRepository),
            importDriver = ImportDriverUseCase(container.driverRepository),
            deleteDriver = DeleteDriverUseCase(container.driverRepository, container.spaceRepository),
            selectDriver = SelectDriverForSpaceUseCase(container.spaceRepository, container.driverRepository),
            launchSpace = LaunchSpaceUseCase(container.virtualEngine, container.spaceRepository),
            observeGpu = ObserveGpuUseCase(container.gpuDetector),
            shizuku = ShizukuStatusUseCase(container.shizukuManager),
        )
    }

    data class Set(
        val createSpace: CreateSpaceUseCase,
        val listSpaces: ListSpacesUseCase,
        val deleteSpace: DeleteSpaceUseCase,
        val installApk: InstallApkUseCase,
        val importDriver: ImportDriverUseCase,
        val deleteDriver: DeleteDriverUseCase,
        val selectDriver: SelectDriverForSpaceUseCase,
        val launchSpace: LaunchSpaceUseCase,
        val observeGpu: ObserveGpuUseCase,
        val shizuku: ShizukuStatusUseCase,
    )
}
