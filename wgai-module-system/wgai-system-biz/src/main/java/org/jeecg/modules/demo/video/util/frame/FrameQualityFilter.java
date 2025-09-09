package org.jeecg.modules.demo.video.util.frame;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Scalar;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 重新设计的视频帧质量检测工具类
 * 修复了误判问题，对正常彩色图片和红外线图片更加友好
 * @author wggg
 * @date 2025/8/6 11:02
 */
@Slf4j
public class FrameQualityFilter {

    // 重新校准的阈值 - 更加保守和准确
    private static final double OBVIOUS_GRAY_SAT_THRESHOLD = 5.0;       // 明显灰度图饱和度阈值
    private static final double OBVIOUS_GRAY_SAT_STD_THRESHOLD = 3.0;   // 明显灰度图饱和度标准差阈值
    private static final double PARTIAL_GRAY_RATIO_THRESHOLD = 0.85;    // 部分灰色区域比例阈值（更严格）
    private static final double SEVERE_BLUR_THRESHOLD = 15.0;           // 严重模糊阈值
    private static final double EXTREME_NOISE_THRESHOLD = 0.6;          // 极端噪声阈值
    private static final double EXTREME_EDGE_DENSITY_THRESHOLD = 0.8;   // 极端边缘密度阈值
    private static final double SNOW_NOISE_DENSITY_THRESHOLD = 0.5;     // 雪花噪声密度阈值
    private static final double MOSAIC_BLOCK_THRESHOLD = 0.3;           // 马赛克块阈值（更严格）
    private static final double MIN_BRIGHTNESS = 5.0;                   // 最小亮度阈值
    private static final double MAX_BRIGHTNESS = 250.0;                 // 最大亮度阈值
    private static final double INFRARED_HUE_TOLERANCE = 15.0;          // 红外线色相容忍度

    public static void main(String[] args) {
        System.load("F:\\JAVAAI\\opencv481\\opencv\\build\\java\\x64\\opencv_java481.dll");
         Java2DFrameConverter converter = new Java2DFrameConverter();
        String filePath="F:\\JAVAAI\\AIImg\\test";
        File directory = new File(filePath);

        if (!directory.isDirectory()) {
            log.info("路径不是一个有效的目录");
            return;
        }

        String[] imageExtensions = { ".jpg", ".jpeg", ".png", ".bmp", ".gif" };

        File[] imageFiles = directory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                for (String ext : imageExtensions) {
                    if (name.toLowerCase().endsWith(ext)) {
                        return true;
                    }
                }
                return false;
            }
        });

        if (imageFiles != null && imageFiles.length > 0) {
            int a=1;
            for (File img : imageFiles) {
//                a++;
//                if(a==5||a==8||a==21||a==35||a==42||a==63){
//                    log.info("处理图片: " + img.getAbsolutePath());
//                }else{
//                    continue;
//                }

                Mat matInfo = Imgcodecs.imread(img.getAbsolutePath());

                Mat resized = new Mat();
                Size size = new Size(20, 20);
                Imgproc.resize(matInfo, resized, size);

                int rows = resized .rows();
                int cols = resized .cols();
                log.info(a+"处理图片:{},{},{} " + img.getAbsolutePath(),rows ,cols);
//                for (int y = 0; y < rows; y++) {
//                    for (int x = 0; x < cols; x++) {
//                        double[] pixel = resized.get(y, x);
//                        if (pixel.length >= 3) {
//                            int b = (int) pixel[0];
//                            int g = (int) pixel[1];
//                            int r = (int) pixel[2];
//                            log.info(img.getAbsolutePath()+"({}, {}): R={}, G={}, B={}", x, y, r, g, b);
//                        }
//                    }
//                }
                if(printAverageRGB(matInfo)){
                    log.info("当前是灰度图");

                }
              //  BufferedImage image = converter.convert(matInfo);
                log.info("-----------------------------------------------");
                a++;
            }
        } else {
            log.info("目录中没有找到图片文件。");
        }
    }
    public static boolean printAverageRGB(Mat image) {
        if (image.empty() || image.channels() < 3) {
            return true; // 单通道肯定是灰图或者单一颜色图片
        }

        
        List<Mat> channels = new ArrayList<>();
        Core.split(image, channels); // B, G, R
        double meanB = Core.mean(channels.get(0)).val[0];
        double meanG = Core.mean(channels.get(1)).val[0];
        double meanR = Core.mean(channels.get(2)).val[0];
        double max = Math.max(meanR, Math.max(meanG, meanB));
        double min = Math.min(meanR, Math.min(meanG, meanB));
        log.debug("Average R={}, G={}, B={}", meanR,meanG, meanB);
        double maxSub=(max - min);
        double sumRGB= (meanR+meanB+meanG)/3;
        log.debug("Average max={},min={},avg={},maxSub={}",max,min, sumRGB,maxSub);
        if( sumRGB>120 && sumRGB<140 && maxSub< 10) {
            //进一步确定是灰度图片吗
            double[] result = getBrightnessAndSaturation(image);
            log.debug("图像亮度(平均V):{}", result[0]);
            log.debug("图像饱和度(平均S): {}", result[1]);
       //     boolean isGray = isGrayByHistogram(image, 0.99);
        //    System.out.println("图像是否为灰图: " + isGray);
            if(result[1]>15){
                log.info("【二次判定不是灰色图片-通过验证】");
                return  false;
            }
            return true;
        }
        return  false;
    }

    /**
     * 检测马赛克/花屏
     */
    private boolean isMosaicFrame(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // 检查颜色突变的频率
        int abruptChanges = 0;
        int totalChecks = 0;
        int threshold = 100; // 颜色差异阈值

        // 水平方向检查
        for (int y = 0; y < height; y += 10) {
            for (int x = 1; x < width; x += 10) {
                Color prev = new Color(image.getRGB(x-1, y));
                Color curr = new Color(image.getRGB(x, y));

                int diff = Math.abs(prev.getRed() - curr.getRed()) +
                        Math.abs(prev.getGreen() - curr.getGreen()) +
                        Math.abs(prev.getBlue() - curr.getBlue());

                if (diff > threshold) {
                    abruptChanges++;
                }
                totalChecks++;
            }
        }

        // 如果突变太频繁，可能是马赛克
        double changeRate = (double)abruptChanges / totalChecks;
        log.info("马赛克检测:{}",changeRate);
        return changeRate > 0.6; // 超过60%的像素有突变
    }


    public static boolean isGrayByHistogram(Mat image, double threshold) {
        if (image.empty() || image.channels() < 3) return true;

        // 拆分 RGB 通道
        List<Mat> bgrPlanes = new ArrayList<>();
        Core.split(image, bgrPlanes); // B, G, R

        // 设置直方图参数
        MatOfInt histSize = new MatOfInt(256); // 256 bins
        final MatOfFloat histRange = new MatOfFloat(0f, 256f);

        boolean accumulate = false;
        Mat histB = new Mat(), histG = new Mat(), histR = new Mat();

        // 计算每个通道的直方图
        Imgproc.calcHist(Arrays.asList(bgrPlanes.get(0)), new MatOfInt(0), new Mat(), histB, histSize, histRange, accumulate);
        Imgproc.calcHist(Arrays.asList(bgrPlanes.get(1)), new MatOfInt(0), new Mat(), histG, histSize, histRange, accumulate);
        Imgproc.calcHist(Arrays.asList(bgrPlanes.get(2)), new MatOfInt(0), new Mat(), histR, histSize, histRange, accumulate);

        // 归一化
        Core.normalize(histB, histB, 0, 1, Core.NORM_MINMAX);
        Core.normalize(histG, histG, 0, 1, Core.NORM_MINMAX);
        Core.normalize(histR, histR, 0, 1, Core.NORM_MINMAX);

        // 比较直方图相似度（使用相关性）
        double corrBG = Imgproc.compareHist(histB, histG, Imgproc.CV_COMP_CORREL);
        double corrGR = Imgproc.compareHist(histG, histR, Imgproc.CV_COMP_CORREL);
        double corrBR = Imgproc.compareHist(histB, histR, Imgproc.CV_COMP_CORREL);

        // 若三个通道直方图高度相似，说明为灰图
        return (corrBG > threshold && corrGR > threshold && corrBR > threshold);
    }

    public static double[] getBrightnessAndSaturation(Mat image) {

        // 转换 BGR 到 HSV
        Mat hsv = new Mat();
        Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);

        // 拆分 HSV 通道：H, S, V
        List<Mat> hsvChannels = new ArrayList<>();
        Core.split(hsv, hsvChannels);

        // 饱和度 = S 通道平均值
        Scalar meanS = Core.mean(hsvChannels.get(1));

        // 亮度 = V 通道平均值
        Scalar meanV = Core.mean(hsvChannels.get(2));

        return new double[]{meanV.val[0], meanS.val[0]};
    }
    /**
     * 综合质量检测 - 只过滤明显有问题的图片
     */
    public static boolean isHighQualityFrame(Mat frame, BufferedImage bufferedImage) {
        if (frame == null || frame.empty()) {
            return false;
        }

        try {
            // 1. 基础尺寸检查
            if (!checkBasicDimensions(frame)) {
                return false;
            }

            // 2. 只检查明显的灰度图（排除红外线图片）
            if (isObviousGrayFrame(frame)) {
                return false;
            }

            // 3. 只检查极端部分灰色情况
            if (isExtremePartialGrayFrame(frame)) {
                return false;
            }

            // 4. 只检查极端马赛克
            if (isExtremeMosaicFrame(frame)) {
                return false;
            }

            // 5. 只检查严重雪花噪声
            if (isSevereSnowFrame(frame)) {
                return false;
            }

            // 6. 只检查严重模糊
            if (isSeverelyBlurred(frame)) {
                return false;
            }

            // 7. 只检查极端花屏
            if (isExtremeGlitch(frame)) {
                return false;
            }

            // 8. 检查极端亮度异常
            if (isExtremeBrightnessAbnormal(frame)) {
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("[帧质量检测异常]", e);
            return false;
        }
    }

    /**
     * 基础尺寸检查
     */
    public static boolean checkBasicDimensions(Mat frame) {
        return frame.rows() > 32 && frame.cols() > 32; // 降低尺寸要求
    }

    /**
     * 检测明显的灰度图 - 排除红外线和夜视图片
     */
    public static boolean isObviousGrayFrame(Mat frame) {
        if (frame.channels() == 1) {
            return true; // 单通道确实是灰度图
        }

        Mat hsv = new Mat();
        List<Mat> channels = new ArrayList<>();

        try {
            Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

            channels.add(new Mat());
            channels.add(new Mat());
            channels.add(new Mat());
            Core.split(hsv, channels);

            Mat saturation = channels.get(1);
            Mat hue = channels.get(0);
            Mat value = channels.get(2);

            // 计算饱和度统计
            Scalar meanSat = Core.mean(saturation);
            MatOfDouble stddevMat = new MatOfDouble();
            MatOfDouble meanMat = new MatOfDouble();
            Core.meanStdDev(saturation, meanMat, stddevMat);

            double avgSaturation = meanSat.val[0];
            double stdSaturation = stddevMat.toArray()[0];

            // 计算亮度
            Scalar meanBrightness = Core.mean(value);
            double avgBrightness = meanBrightness.val[0];

            stddevMat.release();
            meanMat.release();

            // 检查是否为红外线图片（通常偏红色或绿色）
            boolean isInfrared = isInfraredImage(hue, saturation, avgBrightness);

            // 只有极低饱和度且非红外线图片才认为是灰度图
            boolean isObviousGray = (avgSaturation < OBVIOUS_GRAY_SAT_THRESHOLD &&
                    stdSaturation < OBVIOUS_GRAY_SAT_STD_THRESHOLD &&
                    avgBrightness > MIN_BRIGHTNESS &&
                    !isInfrared);

            if (isObviousGray) {
                log.info("检测到明显灰度图 - 平均饱和度: " + avgSaturation +
                        ", 饱和度标准差: " + stdSaturation +
                        ", 平均亮度: " + avgBrightness +
                        ", 红外线: " + isInfrared);
            }

            return isObviousGray;

        } finally {
            if (hsv != null) hsv.release();
            for (Mat channel : channels) {
                if (channel != null) channel.release();
            }
        }
    }

    /**
     * 检测红外线图片
     */
    private static boolean isInfraredImage(Mat hue, Mat saturation, double avgBrightness) {
        try {
            // 红外线图片通常有特定的色相分布（偏红或绿）
            Scalar meanHue = Core.mean(hue);
            double avgHue = meanHue.val[0];

            // 计算饱和度，红外线图片虽然看起来单色，但HSV饱和度可能不为0
            Scalar meanSat = Core.mean(saturation);
            double avgSat = meanSat.val[0];

            // 红外线特征：
            // 1. 色相集中在特定范围（红色0-15度或绿色60-90度）
            // 2. 饱和度适中（不是完全无饱和度）
            // 3. 亮度适中
            boolean isRedInfrared = (avgHue <= INFRARED_HUE_TOLERANCE || avgHue >= (180 - INFRARED_HUE_TOLERANCE))
                    && avgSat > 5.0 && avgSat < 80.0;

            boolean isGreenInfrared = (avgHue >= 40 && avgHue <= 100)
                    && avgSat > 5.0 && avgSat < 80.0;

            return (isRedInfrared || isGreenInfrared) && avgBrightness > 20;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检测极端部分灰度图 - 只检测几乎全部都是灰色的情况
     */
    public static boolean isExtremePartialGrayFrame(Mat frame) {
        Mat hsv = new Mat();
        List<Mat> channels = new ArrayList<>();

        try {
            Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

            channels.add(new Mat());
            channels.add(new Mat());
            channels.add(new Mat());
            Core.split(hsv, channels);

            Mat saturation = channels.get(1);

            // 创建极低饱和度掩码（更严格的阈值）
            Mat lowSatMask = new Mat();
            Imgproc.threshold(saturation, lowSatMask, 130, 150, Imgproc.THRESH_BINARY_INV);

            // 计算极低饱和度区域比例
            int totalPixels = saturation.rows() * saturation.cols();
            int lowSatPixels = Core.countNonZero(lowSatMask);
            double lowSatRatio = (double) lowSatPixels / totalPixels;

            lowSatMask.release();

            // 只有极大比例是灰色才认为有问题
            boolean isExtremePartialGray = lowSatRatio > PARTIAL_GRAY_RATIO_THRESHOLD;

            if (isExtremePartialGray) {
                log.info("检测到极端部分灰度图 - 低饱和度区域比例: " + lowSatRatio);
            }

            return isExtremePartialGray;

        } finally {
            if (hsv != null) hsv.release();
            for (Mat channel : channels) {
                if (channel != null) channel.release();
            }
        }
    }

    /**
     * 检测极端马赛克 - 只检测明显损坏的情况
     */
    public static boolean isExtremeMosaicFrame(Mat frame) {
        Mat gray = new Mat();

        try {
            if (frame.channels() == 3) {
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                frame.copyTo(gray);
            }

            // 检测极端噪声
            double noiseScore = calculateAdvancedNoiseScore(gray);

            // 检测异常块状结构
            double blockiness = calculateStrongBlockiness(gray);

            // 检测边缘密度异常
            double edgeDensity = calculateEdgeDensity(gray);

            // 只有多个指标都异常才认为是马赛克
            boolean isExtremeMosaic = (noiseScore > EXTREME_NOISE_THRESHOLD) &&
                    (blockiness > MOSAIC_BLOCK_THRESHOLD || edgeDensity > EXTREME_EDGE_DENSITY_THRESHOLD);

            if (isExtremeMosaic) {
                log.info("检测到极端马赛克 - 噪声: " + noiseScore +
                        ", 块状度: " + blockiness +
                        ", 边缘密度: " + edgeDensity);
            }

            return isExtremeMosaic;

        } finally {
            if (gray != null) gray.release();
        }
    }

    /**
     * 检测严重雪花噪声 - 更准确的雪花检测
     */
    public static boolean isSevereSnowFrame(Mat frame) {
        Mat gray = new Mat();

        try {
            if (frame.channels() == 3) {
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                frame.copyTo(gray);
            }

            // 使用更大的高斯核来检测雪花
            Mat blurred = new Mat();
            Mat diff = new Mat();

            Imgproc.GaussianBlur(gray, blurred, new Size(9, 9), 2.0);
            Core.absdiff(gray, blurred, diff);

            // 使用更高的阈值
            Mat binary = new Mat();
            Imgproc.threshold(diff, binary, 40, 255, Imgproc.THRESH_BINARY);

            // 计算噪声点密度
            int totalPixels = binary.rows() * binary.cols();
            int noisePixels = Core.countNonZero(binary);
            double noiseDensity = (double) noisePixels / totalPixels;

            // 检测噪声点大小分布
            double avgNoiseSize = calculateAverageNoiseSize(binary);

            blurred.release();
            diff.release();
            binary.release();

            // 雪花特征：高密度 + 小尺寸噪声点
            boolean isSevereSnow = (noiseDensity > SNOW_NOISE_DENSITY_THRESHOLD) && (avgNoiseSize < 3.0);

            if (isSevereSnow) {
                log.info("检测到严重雪花噪声 - 噪声密度: " + noiseDensity +
                        ", 平均噪声大小: " + avgNoiseSize);
            }

            return isSevereSnow;

        } finally {
            if (gray != null) gray.release();
        }
    }

    /**
     * 检测严重模糊 - 考虑图片内容特征
     */
    public static boolean isSeverelyBlurred(Mat frame) {
        Mat gray = new Mat();
        Mat laplacian = new Mat();

        try {
            if (frame.channels() == 3) {
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                frame.copyTo(gray);
            }

            // 检查平均亮度
            Scalar meanBrightness = Core.mean(gray);
            double avgBrightness = meanBrightness.val[0];

            // 计算图像对比度
            MatOfDouble mean = new MatOfDouble();
            MatOfDouble stddev = new MatOfDouble();
            Core.meanStdDev(gray, mean, stddev);
            double contrast = stddev.toArray()[0];

            // 计算拉普拉斯方差
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F);
            MatOfDouble lapMean = new MatOfDouble();
            MatOfDouble lapStddev = new MatOfDouble();
            Core.meanStdDev(laplacian, lapMean, lapStddev);
            double lapVariance = lapStddev.toArray()[0] * lapStddev.toArray()[0];

            mean.release();
            stddev.release();
            lapMean.release();
            lapStddev.release();

            // 根据对比度调整模糊阈值
            double adaptiveBlurThreshold = SEVERE_BLUR_THRESHOLD;
            if (contrast < 20) { // 低对比度图像
                adaptiveBlurThreshold *= 0.5;
            }

            boolean isSeverelyBlurred = (lapVariance < adaptiveBlurThreshold) && (contrast > 10);

            if (isSeverelyBlurred) {
                log.info("检测到严重模糊 - 拉普拉斯方差: " + lapVariance +
                        ", 阈值: " + adaptiveBlurThreshold +
                        ", 对比度: " + contrast);
            }

            return isSeverelyBlurred;

        } finally {
            if (gray != null) gray.release();
            if (laplacian != null) laplacian.release();
        }
    }

    /**
     * 检测极端花屏 - 更保守的花屏检测
     */
    public static boolean isExtremeGlitch(Mat frame) {
        Mat hsv = new Mat();
        List<Mat> channels = new ArrayList<>();

        try {
            Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

            channels.add(new Mat());
            channels.add(new Mat());
            channels.add(new Mat());
            Core.split(hsv, channels);

            // 检测饱和度异常
            Mat saturation = channels.get(1);
            MatOfDouble satMean = new MatOfDouble();
            MatOfDouble satStdDev = new MatOfDouble();
            Core.meanStdDev(saturation, satMean, satStdDev);

            double saturationStd = satStdDev.toArray()[0];
            double saturationMean = satMean.toArray()[0];

            // 检测亮度异常
            Mat value = channels.get(2);
            MatOfDouble valueMean = new MatOfDouble();
            MatOfDouble valueStdDev = new MatOfDouble();
            Core.meanStdDev(value, valueMean, valueStdDev);

            double valueStd = valueStdDev.toArray()[0];
            double valueMeanVal = valueMean.toArray()[0];

            // 检测色相跳跃
            Mat hue = channels.get(0);
            double hueJump = calculateSafeHueJump(hue);

            satMean.release();
            satStdDev.release();
            valueMean.release();
            valueStdDev.release();

            // 极端花屏：多个通道都有剧烈变化 且 亮度不是极端值
            boolean isExtremeGlitch = (saturationStd > 70.0 && valueStd > 70.0 && hueJump > 2000.0) &&
                    (valueMeanVal > 20 && valueMeanVal < 235);

            if (isExtremeGlitch) {
                log.info("检测到极端花屏 - 饱和度标准差: " + saturationStd +
                        ", 亮度标准差: " + valueStd +
                        ", 色相跳跃: " + hueJump);
            }

            return isExtremeGlitch;

        } finally {
            if (hsv != null) hsv.release();
            for (Mat channel : channels) {
                if (channel != null) channel.release();
            }
        }
    }

    /**
     * 检测极端亮度异常
     */
    public static boolean isExtremeBrightnessAbnormal(Mat frame) {
        Mat gray = new Mat();

        try {
            if (frame.channels() == 3) {
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            } else {
                frame.copyTo(gray);
            }

            Scalar meanBrightness = Core.mean(gray);
            double avgBrightness = meanBrightness.val[0];

            // 只检测极端情况：几乎全黑或全白
            boolean isExtremelyDark = avgBrightness < MIN_BRIGHTNESS;
            boolean isExtremelyBright = avgBrightness > MAX_BRIGHTNESS;

            boolean isAbnormal = isExtremelyDark || isExtremelyBright;

            if (isAbnormal) {
                log.info("检测到极端亮度异常 - 平均亮度: " + avgBrightness);
            }

            return isAbnormal;

        } finally {
            if (gray != null) gray.release();
        }
    }

    // 辅助计算方法

    /**
     * 改进的噪声评分计算
     */
    private static double calculateAdvancedNoiseScore(Mat gray) {
        Mat median = new Mat();
        Mat diff = new Mat();

        try {
            // 使用中值滤波代替形态学操作
            Imgproc.medianBlur(gray, median, 5);
            Core.absdiff(gray, median, diff);

            // 计算噪声的统计特征
            MatOfDouble mean = new MatOfDouble();
            MatOfDouble stddev = new MatOfDouble();
            Core.meanStdDev(diff, mean, stddev);

            double noiseStd = stddev.toArray()[0];

            mean.release();
            stddev.release();

            return noiseStd / 255.0;

        } finally {
            median.release();
            diff.release();
        }
    }

    /**
     * 计算强块状度 - 更准确的马赛克检测
     */
    private static double calculateStrongBlockiness(Mat gray) {
        Mat sobelX = new Mat();
        Mat sobelY = new Mat();

        try {
            // 使用更大的Sobel核
            Imgproc.Sobel(gray, sobelX, CvType.CV_64F, 1, 0, 5);
            Imgproc.Sobel(gray, sobelY, CvType.CV_64F, 0, 1, 5);

            // 计算梯度幅值
            Mat magnitude = new Mat();
            Core.magnitude(sobelX, sobelY, magnitude);

            // 计算梯度的峰值密度（马赛克会有很多尖锐边缘）
            Mat binary = new Mat();
            Imgproc.threshold(magnitude, binary, 100, 255, Imgproc.THRESH_BINARY);

            int totalPixels = binary.rows() * binary.cols();
            int edgePixels = Core.countNonZero(binary);
            double edgeRatio = (double) edgePixels / totalPixels;

            magnitude.release();
            binary.release();

            return edgeRatio;

        } finally {
            sobelX.release();
            sobelY.release();
        }
    }

    /**
     * 计算边缘密度
     */
    private static double calculateEdgeDensity(Mat gray) {
        Mat edges = new Mat();

        try {
            // 使用适中的Canny参数
            Imgproc.Canny(gray, edges, 80, 160);

            int totalPixels = edges.rows() * edges.cols();
            int edgePixels = Core.countNonZero(edges);
            return (double) edgePixels / totalPixels;

        } finally {
            edges.release();
        }
    }

    /**
     * 计算平均噪声大小
     */
    private static double calculateAverageNoiseSize(Mat binary) {
        // 使用连通组件分析
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();

        try {
            int numLabels = Imgproc.connectedComponentsWithStats(binary, labels, stats, centroids);

            if (numLabels <= 1) {
                return 0.0;
            }

            double totalArea = 0;
            int validComponents = 0;

            // 遍历每个连通组件（跳过背景）
            for (int i = 1; i < numLabels; i++) {
                double[] statsData = stats.get(i, 0);
                if (statsData != null && statsData.length >= 5) {
                    double area = statsData[4]; // 面积
                    if (area > 0 && area < 50) { // 只考虑小噪声点
                        totalArea += area;
                        validComponents++;
                    }
                }
            }

            return validComponents > 0 ? totalArea / validComponents : 0.0;

        } finally {
            labels.release();
            stats.release();
            centroids.release();
        }
    }

    /**
     * 安全的色相跳跃计算
     */
    private static double calculateSafeHueJump(Mat hue) {
        Mat diffX = new Mat();
        Mat diffY = new Mat();

        try {
            // 计算色相梯度
            Imgproc.Sobel(hue, diffX, CvType.CV_32F, 1, 0, 3);
            Imgproc.Sobel(hue, diffY, CvType.CV_32F, 0, 1, 3);

            // 计算梯度幅值
            Mat magnitude = new Mat();
            Core.magnitude(diffX, diffY, magnitude);

            // 计算平均梯度幅值
            Scalar meanGradient = Core.mean(magnitude);
            magnitude.release();

            return meanGradient.val[0];

        } finally {
            diffX.release();
            diffY.release();
        }
    }

    /**
     * 快速质量检测
     */
    public static boolean isHighQualityFrameFast(Mat frame) {
        if (frame == null || frame.empty()) {
            return false;
        }

        return checkBasicDimensions(frame) &&
                !isObviousGrayFrame(frame) &&
                !isExtremeBrightnessAbnormal(frame);
    }

    /**
     * 获取帧质量评分 (0-100)
     */
    public static double getFrameQualityScore(Mat frame) {
        if (frame == null || frame.empty()) {
            return 0;
        }

        double score = 20; // 基础分数

        try {
            // 尺寸检查 (10分)
            if (checkBasicDimensions(frame)) {
                score += 10;
            }

            // 非明显灰度检查 (20分)
            if (!isObviousGrayFrame(frame)) {
                score += 20;
            }

            // 非极端部分灰度检查 (15分)
            if (!isExtremePartialGrayFrame(frame)) {
                score += 15;
            }

            // 非极端马赛克检查 (15分)
            if (!isExtremeMosaicFrame(frame)) {
                score += 15;
            }

            // 非严重雪花检查 (10分)
            if (!isSevereSnowFrame(frame)) {
                score += 10;
            }

            // 非严重模糊检查 (10分)
            if (!isSeverelyBlurred(frame)) {
                score += 10;
            }

            return Math.min(100, score);

        } catch (Exception e) {
            log.error("[质量评分异常]", e);
            return 20; // 返回基础分数
        }
    }


}