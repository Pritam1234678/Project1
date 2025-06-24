import org.opencv.core.*;
import org.opencv.dnn.*;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.highgui.HighGui;

import java.util.Arrays;

public class EmotionDetector {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    static final String[] EMOTIONS = {
            "Angry", "Disgust", "Fear", "Happy", "Sad", "Surprise", "Neutral"
    };

    public static void main(String[] args) {
        VideoCapture camera = new VideoCapture(0);
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        if (!camera.isOpened()) {
            System.out.println("❌ Cannot access camera at index 0. Trying index 1...");
            camera.open(1);
            try { Thread.sleep(1000); } catch (InterruptedException e2) {}
            if (!camera.isOpened()) {
                System.out.println("❌ Camera still not available. Exiting.");
                return;
            }
        }

        CascadeClassifier faceDetector = new CascadeClassifier("src/haarcascade_frontalface_default.xml");
        if (faceDetector.empty()) {
            System.out.println("❌ Failed to load face detector XML file.");
            return;
        }

        Net emotionModel = Dnn.readNetFromONNX("src/onnx_model.onnx");
        if (emotionModel.empty()) {
            System.out.println("❌ Failed to load ONNX emotion model.");
            return;
        }

        Mat frame = new Mat();

        while (true) {
            camera.read(frame);
            if (frame.empty()) continue;

            Mat gray = new Mat();
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            MatOfRect faces = new MatOfRect();
            faceDetector.detectMultiScale(gray, faces);

            for (Rect rect : faces.toArray()) {
                Imgproc.rectangle(frame, rect, new Scalar(0, 255, 0), 2);

                // Extract and preprocess grayscale face
                Mat faceGray = new Mat(gray, rect);
                Imgproc.resize(faceGray, faceGray, new Size(48, 48));
                faceGray.convertTo(faceGray, CvType.CV_32F, 1.0 / 255.0);

                // Create blob: [1, 1, 48, 48]
                Mat blob = Dnn.blobFromImage(faceGray, 1.0, new Size(48, 48),
                        new Scalar(0), false, false, CvType.CV_32F);

                emotionModel.setInput(blob);
                Mat output = emotionModel.forward(); // Shape: [1, 7]

                double[] probs = softmax(output);
                int maxIdx = 0;
                double maxProb = probs[0];
                for (int i = 1; i < probs.length; i++) {
                    if (probs[i] > maxProb) {
                        maxProb = probs[i];
                        maxIdx = i;
                    }
                }

                String emotion = EMOTIONS[maxIdx];
                Imgproc.putText(frame, "Mood: " + emotion,
                        new Point(rect.x, rect.y - 10),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.7,
                        new Scalar(0, 255, 255), 2);

                System.out.println("Probs: " + Arrays.toString(probs));
            }

            HighGui.imshow("Java Mood Detector (press 'q' to quit)", frame);
            if (HighGui.waitKey(1) == 'q') break;
        }

        camera.release();
        HighGui.destroyAllWindows();
    }

    private static double[] softmax(Mat scores) {
        float[] data = new float[(int) scores.total()];
        scores.get(0, 0, data);

        float max = Float.NEGATIVE_INFINITY;
        for (float val : data) if (val > max) max = val;

        double[] exps = new double[data.length];
        double sum = 0.0;
        for (int i = 0; i < data.length; i++) {
            exps[i] = Math.exp(data[i] - max);
            sum += exps[i];
        }

        for (int i = 0; i < exps.length; i++) {
            exps[i] /= sum;
        }

        return exps;
    }
}
