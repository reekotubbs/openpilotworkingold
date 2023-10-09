package ai.flow.common.transformations;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

public class Camera {

    public static final int CAMERA_TYPE_ROAD = 0;
    public static final float f_focal_length = 910.0f;
    public static final int[] frameSize = new int[]{1280, 720};
    public static final String fcamIntrinsicParam = "CameraMatrix";
    public static INDArray fcam_intrinsics = Nd4j.createFromArray(new float[][]{
            {f_focal_length,  0.0f,  frameSize[0]/2f},
            {0.0f,  f_focal_length,  frameSize[1]/2f},
            {0.0f,  0.0f,  1.0f}
    });

    public static final INDArray view_from_device = Nd4j.createFromArray(new float[][]{
            {0.0f,  1.0f,  0.0f},
            {0.0f,  0.0f,  1.0f},
            {1.0f,  0.0f,  0.0f}
    });
}
