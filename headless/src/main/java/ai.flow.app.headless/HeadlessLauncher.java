package ai.flow.app.headless;

import ai.flow.app.FlowUI;
import ai.flow.common.Path;
import ai.flow.common.SystemUtils;
import ai.flow.common.transformations.Camera;
import ai.flow.hardware.DesktopHardwareManager;
import ai.flow.hardware.HardwareManager;
import ai.flow.launcher.Launcher;
import ai.flow.modeld.*;
import ai.flow.sensor.SensorInterface;
import ai.flow.sensor.SensorManager;
import ai.flow.sensor.camera.CameraManager;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static ai.flow.common.SystemUtils.getUseGPU;


/** Launches the headless application */
public class HeadlessLauncher {
	public static void main(String[] args) throws IOException {
		createApplication();
	}

	private static Application createApplication() throws IOException {
		// Note: you can use a custom ApplicationListener implementation for the headless project instead of FlowUI.
		CameraManager fCameraManager = new CameraManager(20, System.getenv("ROAD_CAMERA_SOURCE"), Camera.frameSize[0], Camera.frameSize[1]);

		SensorManager sensorManager = new SensorManager();
		Map<String, SensorInterface> sensors = new HashMap<String, SensorInterface>() {{
			put("roadCamera", fCameraManager);
			put("motionSensors", sensorManager);
		}};

		String modelPath = Path.getModelDir();

		// onnx CPU performs better than TNN.
		ModelRunner model;
		if (getUseGPU())
			model = new TNNModelRunner(modelPath, getUseGPU());
		else
			model = new ONNXModelRunner(modelPath, getUseGPU());

		ModelExecutor modelExecutor;
		modelExecutor = new ModelExecutorF2(model);

		Launcher launcher = new Launcher(sensors, modelExecutor);
		HardwareManager hardwareManager = new DesktopHardwareManager();
		return new HeadlessApplication(new FlowUI(launcher, hardwareManager, SystemUtils.getPID()), getDefaultConfiguration());
	}

	private static HeadlessApplicationConfiguration getDefaultConfiguration() {
		HeadlessApplicationConfiguration configuration = new HeadlessApplicationConfiguration();
		configuration.updatesPerSecond = -1; // When this value is negative, FlowUI#render() is never called.
		return configuration;
	}
}
