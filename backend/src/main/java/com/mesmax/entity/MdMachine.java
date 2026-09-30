package com.mesmax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 机床（CNC 设备）主数据。协议字段标识该机床的联网方式：FOCAS / OPCUA / MTConnect / MQTT。 */
@Data
@TableName("md_machine")
public class MdMachine {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String macCode;
    private String macName;
    private String macModel;
    private String brand;
    /** 立式加工中心 / 卧式加工中心 / 数控车床 / 车削中心 / 外圆磨床 / 内圆磨床 / 线切割 / 电火花 / 五轴加工中心 */
    private String cncType;
    private Long workCenterId;
    private Long workshopId;
    private Integer axisCount;
    private Integer maxSpindleSpeed;
    private BigDecimal travelX;
    private BigDecimal travelY;
    private BigDecimal travelZ;
    private Integer toolMagazineCapacity;
    private String controllerType;
    private String protocol;
    private String ipAddr;
    /** 运行 / 待机 / 停机 / 报警 / 离线 / 保养中 */
    private String status;
    private String runState;
    private BigDecimal oeeTarget;
    private LocalDate purchaseDate;
    private String remark;
}
