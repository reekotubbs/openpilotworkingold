package ai.flow.modeld;

import ai.flow.common.transformations.Camera;
import ai.flow.common.transformations.Model;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.inverse.InvertMatrix;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;

import static org.opencv.imgproc.Imgproc.getPerspectiveTransform;


public class Preprocess {

    private static final long[] YUVimgShape = {384, 512, 1};
    private static final ArrayList<INDArrayIndex[]> slices = getSlices();
    private static final int[] idxTensor0 = {0, 1, 2, 3, 4, 5};
    private static final int[] idxTensor1 = {6, 7, 8, 9, 10, 11};
    public static INDArray sbigmodel_from_calib = Model.sbigmodel_intrinsics.mmul(Camera.view_from_device);
    public static INDArray medmodel_from_calib = Model.medmodel_intrinsics.mmul(Camera.view_from_device);
    public static INDArray getWrapMatrix(INDArray rpy_calib, INDArray f_intrinsics, INDArray e_intrinsics, boolean wide_cam, boolean big_model) {
        INDArray intrinsics;
        if (wide_cam)
            intrinsics = e_intrinsics;
        else
            intrinsics = f_intrinsics;

        INDArray calib_from_model;
        if (big_model)
            calib_from_model = InvertMatrix.invert(sbigmodel_from_calib, false);
        else
            calib_from_model = InvertMatrix.invert(medmodel_from_calib, false);

        INDArray device_from_calib = eulerAnglesToRotationMatrix(rpy_calib.getDouble(0), rpy_calib.getDouble(1), rpy_calib.getDouble(2), false);
        INDArray camera_from_calib = intrinsics.mmul(Camera.view_from_device.mmul(device_from_calib));
        INDArray warp_matrix = camera_from_calib.mmul(calib_from_model);
        return warp_matrix;
    }

    private static ArrayList<INDArrayIndex[]> getSlices(){
        ArrayList<INDArrayIndex[]> slices = new ArrayList<INDArrayIndex[]>();
        for (int i=0; i<12; i++){
            slices.add(new INDArrayIndex[]{NDArrayIndex.point(0), NDArrayIndex.point(i), NDArrayIndex.all(), NDArrayIndex.all()});
        }
        return slices;
    }

    public static void TransformImg(Mat imgRGB888, Mat transformedRGB888, INDArray M, Size output_size){
        INDArray M_inv = InvertMatrix.invert(M, false);
        Mat M_inv_mat = new Mat(3, 3, CvType.CV_32F);
        M_inv_mat.put(0, 0, M_inv.data().asFloat());
        Imgproc.warpPerspective(imgRGB888, transformedRGB888, M_inv_mat, output_size);
        M_inv_mat.release();
        M_inv.close();
    }

    public static void RGB888toYUV420(Mat imgRGB888, Mat imgYUV420){
		Imgproc.cvtColor(imgRGB888, imgYUV420, Imgproc.COLOR_RGB2YUV_I420);
    }

    public static void YUV420toTensor(INDArray YUVNd, INDArray tensor, int sequenceId){

        int H = (int)YUVimgShape[0] * 2/3;
        int W = (int)YUVimgShape[1];

        int[] idxTensor = (sequenceId==0) ? (idxTensor0):(idxTensor1);

        tensor.put(slices.get(idxTensor[0]),
                   YUVNd.get(NDArrayIndex.interval(0, 2, H), NDArrayIndex.interval(0, 2, W)));
        tensor.put(slices.get(idxTensor[1]),
                   YUVNd.get(NDArrayIndex.interval(1, 2, H), NDArrayIndex.interval(0, 2, W)));
        tensor.put(slices.get(idxTensor[2]),
                   YUVNd.get(NDArrayIndex.interval(0, 2, H), NDArrayIndex.interval(1, 2, W)));
        tensor.put(slices.get(idxTensor[3]),
                   YUVNd.get(NDArrayIndex.interval(1, 2, H), NDArrayIndex.interval(1, 2, W)));

        tensor.put(slices.get(idxTensor[4]),
                   YUVNd.get(NDArrayIndex.interval(H, H + H/4), NDArrayIndex.interval(0, W)).reshape(H/2, W/2));
        tensor.put(slices.get(idxTensor[5]),
                   YUVNd.get(NDArrayIndex.interval(H + H/4, H + H/2), NDArrayIndex.interval(0, W)).reshape(H/2, W/2));
    }

    public static INDArray eulerAnglesToRotationMatrix(double roll, double pitch, double yaw, double height, boolean isDegrees){
        INDArray R = eulerAnglesToRotationMatrix(roll, pitch, yaw, isDegrees);

        float[] t_buffer = {0, (float)height, 0};
        INDArray t = Nd4j.create(t_buffer, 1, 3);

        return Nd4j.vstack(R, t).transpose();
    }

    public static INDArray eulerAnglesToRotationMatrix(double roll, double pitch, double yaw, boolean isDegrees){
        if (isDegrees){
            yaw = Math.toRadians(yaw);
            pitch = Math.toRadians(pitch);
            roll = Math.toRadians(roll);
        }

        float rot[][] = new float[3][3];

        float cp = (float) Math.cos(pitch);
        float sp = (float) Math.sin(pitch);
        float sr = (float) Math.sin(roll);
        float cr = (float) Math.cos(roll);
        float sy = (float) Math.sin(yaw);
        float cy = (float) Math.cos(yaw);

        rot[0][0] = cp * cy;
        rot[1][0] = (sr * sp * cy) - (cr * sy);
        rot[2][0] = (cr * sp * cy) + (sr * sy);
        rot[0][1] = cp * sy;
        rot[1][1] = (sr * sp * sy) + (cr * cy);
        rot[2][1] = (cr * sp * sy) - (sr * cy);
        rot[0][2] = -sp;
        rot[1][2] = sr * cp;
        rot[2][2] = cr * cp;

        INDArray R = Nd4j.create(rot);
        return R.transpose();
    }

    public static INDArray euler2quat(INDArray euler) {
        INDArray eulers2d = Nd4j.create(1, euler.shape()[0]);
        eulers2d.putRow(0, euler);

        float gamma = eulers2d.getColumn(0).getFloat();
        float theta = eulers2d.getColumn(1).getFloat();
        float psi   = eulers2d.getColumn(2).getFloat();

        float q0 = (float) (Math.cos(gamma / 2) * Math.cos(theta / 2) * Math.cos(psi / 2) +
                    Math.sin(gamma / 2) * Math.sin(theta / 2) * Math.sin(psi / 2));

        float q1 = (float) (Math.sin(gamma / 2) * Math.cos(theta / 2) * Math.cos(psi / 2) -
                    Math.cos(gamma / 2) * Math.sin(theta / 2) * Math.sin(psi / 2));

        float q2 = (float) (Math.cos(gamma / 2) * Math.sin(theta / 2) * Math.cos(psi / 2) +
                    Math.sin(gamma / 2) * Math.cos(theta / 2) * Math.sin(psi / 2));

        float q3 = (float) (Math.cos(gamma / 2) * Math.cos(theta / 2) * Math.sin(psi / 2) -
                            Math.sin(gamma / 2) * Math.sin(theta / 2) * Math.cos(psi / 2));

        float[][] quats_buffer = {{q0}, {q1}, {q2}, {q3}};
        INDArray quats = Nd4j.createFromArray(quats_buffer);

        for(int i=0; i<quats.shape()[0]; i++)
            if (quats.getFloat(i, 0) < 0)
                quats.putScalar(i,0, -quats.getFloat(i, 0));

        long[] output_shape = {4};

        return quats.reshape(output_shape);
    }

    public static INDArray quat2rot(INDArray quats) {
        INDArray quats2d = Nd4j.create(1, quats.shape()[0]);
        quats2d.putRow(0, quats);
        long[] input_shape = quats2d.shape();
        INDArray Rs = Nd4j.zeros(input_shape[0], 3, 3);

        INDArray q0 = quats2d.getColumn(0);
        INDArray q1 = quats2d.getColumn(1);
        INDArray q2 = quats2d.getColumn(2);
        INDArray q3 = quats2d.getColumn(3);

        Rs.putScalar(0, 0, 0,
                    q0.mul(q0).add(q1.mul(q1)).sub(q2.mul(q2)).sub(q3.mul(q3)).getFloat(0));
        Rs.putScalar(0, 0, 1,
                    q1.mul(q2).sub(q0.mul(q3)).mul(2).getFloat(0));
        Rs.putScalar(0, 0, 2,
                    q0.mul(q2).add(q1.mul(q3)).mul(2).getFloat(0));
        Rs.putScalar(0, 1, 0,
                    q0.mul(q2).add(q1.mul(q3)).mul(2).getFloat(0));
        Rs.putScalar(0, 1, 1,
                    q0.mul(q0).sub(q1.mul(q1)).add(q2.mul(q2)).sub(q3.mul(q3)).getFloat(0));
        Rs.putScalar(0, 1, 2,
                    q2.mul(q3).sub(q0.mul(q1)).mul(2).getFloat(0));
        Rs.putScalar(0, 2, 0,
                    q1.mul(q3).add(q0.mul(q2)).mul(2).getFloat(0));
        Rs.putScalar(0, 2, 1,
                    q0.mul(q1).add(q2.mul(q3)).mul(2).getFloat(0));
        Rs.putScalar(0, 2, 2,
                    q0.mul(q0).sub(q1.mul(q1)).sub(q2.mul(q2)).add(q3.mul(q3)).getFloat(0));

        if(input_shape.length < 2) {
            return Rs.reshape(3,3);
        }
        else{
            return Rs.reshape(3,3);
        }
    }

    public static INDArray normalize(INDArray img_pts, INDArray intrinsics) {
        INDArray intrinsic_inv = InvertMatrix.invert(intrinsics, false);
        long[] input_shape = img_pts.shape();
        INDArray img_pts_hstack = Nd4j.hstack(img_pts, Nd4j.ones(input_shape[0], 1));
        INDArray img_pts_normalized = img_pts_hstack.mmul(intrinsic_inv.transpose());
        return img_pts_normalized.get(NDArrayIndex.all(), NDArrayIndex.interval(0,2)).reshape(input_shape);
    }

    public static INDArray innerProduct(INDArray A, INDArray B){
        ArrayList<INDArray> C = new ArrayList<>();
        for (int i=0; i<(int)A.shape()[0]; i++){
            C.add(B.mul(A.getRow(i)).sum(1));
        }
        return Nd4j.vstack(C);
    }

    public static Mat deviceToCalibratedFrame(float h, float[] frameSize, INDArray fromIntrinsics, INDArray toIntrinsics, INDArray augmentEulers, INDArray augmentTrans) {

        float cy = fromIntrinsics.getFloat(1, 2);
        float[][] quadrangle_buffer = {
            {0, cy+20},
            {frameSize[1]-1, cy+20},
            {0, frameSize[0]-1},
            {frameSize[1]-1, frameSize[0]-1}
        };

        INDArray quadrangle = Nd4j.createFromArray(quadrangle_buffer);
        INDArray quadrangle_norm = Nd4j.hstack(Preprocess.normalize(quadrangle, fromIntrinsics), Nd4j.ones(4,1));

        INDArray quadrangle_world = Nd4j.vstack(
                                    (quadrangle_norm.get(NDArrayIndex.all(), NDArrayIndex.point(0)).mul(h)).div(quadrangle_norm.get(NDArrayIndex.all(), NDArrayIndex.point(1))),
                                    Nd4j.ones(4).mul(h),
                                    Nd4j.ones(4).mul(h).div(quadrangle_norm.get(NDArrayIndex.all(), NDArrayIndex.point(1)))).transpose();

        INDArray to_extrinsic = Preprocess.eulerAnglesToRotationMatrix(augmentEulers.getDouble(0, 0), augmentEulers.getDouble(0, 1), augmentEulers.getDouble(0, 2), augmentTrans.getDouble(0, 0), false);
        INDArray to_KE = toIntrinsics.mmul(to_extrinsic);

        INDArray warped_quadrangle = innerProduct(to_KE, Nd4j.hstack(quadrangle_world, Nd4j.ones(4, 1))).transpose(); //einsum

        warped_quadrangle = warped_quadrangle.div(warped_quadrangle.getColumn(2).reshape(warped_quadrangle.shape()[0], 1));
        warped_quadrangle = warped_quadrangle.get(NDArrayIndex.all(), NDArrayIndex.interval(0,2));

        Mat quadrangle_mat = new Mat(4, 2, CvType.CV_32F);
        Mat warped_quadrangle_mat = new Mat(4, 2, CvType.CV_32F);

        quadrangle_mat.put(0, 0, quadrangle.reshape(4*2).data().asFloat());
        warped_quadrangle_mat.put(0, 0, warped_quadrangle.reshape(4*2).data().asFloat());

        return getPerspectiveTransform(quadrangle_mat, warped_quadrangle_mat);
    }
}
