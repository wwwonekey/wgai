package org.jeecg.modules.tab.AIModel;

import lombok.Data;
import org.opencv.dnn.Net;

/**
 * @author wggg
 * @date 2025/2/24 11:26
 */
@Data
public class NetPush {

    Integer index;
    Net net;
    String modelType;
}
