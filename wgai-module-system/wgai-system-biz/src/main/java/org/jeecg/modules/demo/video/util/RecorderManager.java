package org.jeecg.modules.demo.video.util;


import java.util.concurrent.ConcurrentHashMap;
/**
 * @author wggg
 * @date 2025/6/10 11:04
 */

public class RecorderManager {

    private static final ConcurrentHashMap<String, AlarmVideoRecorder> recorderMap = new ConcurrentHashMap<>();

    public static AlarmVideoRecorder getOrCreateRecorder(String streamId, int width, int height, double fps, int audioChannels) {
        return recorderMap.computeIfAbsent(streamId, id -> new AlarmVideoRecorder(width, height, fps, audioChannels));
    }

    public static void stopAll() {
        for (AlarmVideoRecorder recorder : recorderMap.values()) {
            recorder.stop();
        }
    }

    public static void removeRecorder(String streamId) {
        AlarmVideoRecorder recorder = recorderMap.remove(streamId);
        if (recorder != null) {
            recorder.stop();
        }
    }
}
