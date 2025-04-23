package org.jeecg.modules.demo.szr.util;

import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author wggg
 * @date 2025/4/10 15:01
 */
public class szrUtil {

    public static void main(String[] args) throws Exception {

        System.load("F:\\JAVAAI\\opencv481\\opencv\\build\\java\\x64\\opencv_java481.dll");

        String faceImagePath = "F:\\JAVAAI\\shuziren\\zhangwei.png";
        String audioPath = "F:\\JAVAAI\\shuziren\\1742880652832.wav";
        String mouthCascadePath = "F:\\JAVAAI\\shuziren\\Mouth.xml";

        // 1. 加载图像
        Mat faceImage = Imgcodecs.imread(faceImagePath);
        if (faceImage.empty()) {
            System.err.println("无法加载人脸图像！");
            return;
        }

        // 2. 转灰度
        Mat gray = new Mat();
        Imgproc.cvtColor(faceImage, gray, Imgproc.COLOR_BGR2GRAY);

        // 3. 加载嘴巴检测模型
        CascadeClassifier mouthDetector = new CascadeClassifier(mouthCascadePath);
        if (mouthDetector.empty()) {
            System.err.println("无法加载嘴巴 Haar 模型！");
            return;
        }

        // 4. 检测嘴巴区域
        MatOfRect mouths = new MatOfRect();
        mouthDetector.detectMultiScale(gray, mouths, 1.1, 5, 0, new Size(30, 30), new Size());

        Rect mouth = mouths.toArray().length > 0 ? mouths.toArray()[0] : null;
        if (mouth == null) {
            System.err.println("未检测到嘴巴区域！");
            return;
        }

        // 5. 分析音频节奏
        List<Boolean> openMouthFrames = analyzeAudioRhythm(audioPath, 25);  // 25帧每秒

        // 6. 每帧处理嘴巴区域（放大/缩小）
        for (int i = 0; i < openMouthFrames.size(); i++) {
            Mat frame = faceImage.clone();
            Rect roi = new Rect(mouth.x, mouth.y, mouth.width, mouth.height);
            Mat mouthROI = new Mat(frame, roi);

            // 放大嘴巴区域高度（模拟张嘴）
            if (openMouthFrames.get(i)) {
                Mat stretched = new Mat();
                Size newSize = new Size(mouthROI.cols(), mouthROI.rows() + 20);
                Imgproc.resize(mouthROI, stretched, newSize);
                stretched.copyTo(frame.submat(new Rect(mouth.x, mouth.y, stretched.cols(), stretched.rows())));
            }
            System.out.println("输出");
            // 保存帧图像
            Imgcodecs.imwrite("F:\\JAVAAI\\shuziren\\outputframes\\rame_" + String.format("%03d", i) + ".jpg", frame);
        }

        System.out.println("处理完成！请使用 ffmpeg 合成视频。");
    }

    // 简化：用音量作为判断标准（帧率 = 25fps）
    public static List<Boolean> analyzeAudioRhythm(String audioPath, int fps) throws Exception {
        List<Boolean> openMouth = new ArrayList<>();

        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(audioPath));
        AudioFormat format = audioInputStream.getFormat();
        byte[] audioBytes =readAllBytes(audioInputStream);

        int frameSize = (int) (format.getFrameRate() / fps);
        int bytesPerSample = format.getFrameSize();

        for (int i = 0; i < audioBytes.length; i += frameSize * bytesPerSample) {
            double sum = 0;
            for (int j = 0; j < frameSize * bytesPerSample && i + j < audioBytes.length; j += 2) {
                short sample = (short) ((audioBytes[i + j + 1] << 8) | (audioBytes[i + j] & 0xff));
                sum += Math.abs(sample);
            }
            double avg = sum / frameSize;
            openMouth.add(avg > 500);  // 简单阈值
        }

        return openMouth;
    }
    public static byte[] readAllBytes(AudioInputStream audioInputStream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] temp = new byte[4096];
        int bytesRead;
        while ((bytesRead = audioInputStream.read(temp)) != -1) {
            buffer.write(temp, 0, bytesRead);
        }
        return buffer.toByteArray();
    }
}
