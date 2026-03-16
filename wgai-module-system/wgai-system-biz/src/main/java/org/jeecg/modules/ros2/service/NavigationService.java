package org.jeecg.modules.ros2.service;



import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 导航服务
 * 处理导航相关业务逻辑
 */
@Slf4j
@Service
public class NavigationService {

    @Autowired
    private ROS2BridgeService ros2Bridge;

    /**
     * 发送导航目标
     */
    public void sendNavigationGoal(Double x, Double y, Double theta) {
        JsonObject msg = new JsonObject();

        // Header
        JsonObject header = new JsonObject();
        header.addProperty("frame_id", "map");
        msg.add("header", header);

        // Pose
        JsonObject pose = new JsonObject();

        // Position
        JsonObject position = new JsonObject();
        position.addProperty("x", x);
        position.addProperty("y", y);
        position.addProperty("z", 0.0);

        // Orientation (从角度转换为四元数)
        JsonObject orientation = new JsonObject();
        double halfTheta = theta / 2;
        orientation.addProperty("x", 0.0);
        orientation.addProperty("y", 0.0);
        orientation.addProperty("z", Math.sin(halfTheta));
        orientation.addProperty("w", Math.cos(halfTheta));

        pose.add("position", position);
        pose.add("orientation", orientation);
        msg.add("pose", pose);

        // 发布到 ROS2
        ros2Bridge.publish("/goal_pose", "geometry_msgs/PoseStamped", msg);

        log.info("已发送导航目标: x={}, y={}, theta={}", x, y, theta);
    }

    /**
     * 取消导航
     */
    public void cancelNavigation() {
        JsonObject cancelMsg = new JsonObject();
        ros2Bridge.publish("/cancel_navigation", "std_msgs/Empty", cancelMsg);
        log.info("已取消导航");
    }
}
