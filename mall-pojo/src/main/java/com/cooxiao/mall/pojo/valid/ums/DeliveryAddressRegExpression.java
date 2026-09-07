package com.cooxiao.mall.pojo.valid.ums;

import com.cooxiao.mall.common.validation.RegExpressions;

/**
 * 收货地址校验规则（DeliveryAddressAddDTO / DeliveryAddressEditDTO）
 * 正则参考 seckill.SeckillOrderRegExpression（秒杀下单同款联系方式校验）
 */
public interface DeliveryAddressRegExpression extends RegExpressions {

    String REGEXP_CONTACT_NAME = ".{2,20}";
    String MESSAGE_CONTACT_NAME = "联系人姓名必须为 2~20 个字符！";

    String REGEXP_MOBILE_PHONE = "^1(?:3\\d|4[4-9]|5[0-35-9]|6[67]|7[013-8]|8\\d|9\\d)\\d{8}$";
    String MESSAGE_MOBILE_PHONE = "请输入中国大陆有效手机号！";

    String REGEXP_TELEPHONE = "0\\d{2,3}-\\d{7,8}|\\(?0\\d{2,3}[)-]?\\d{7,8}|\\(?0\\d{2,3}[)-]*\\d{7,8}";
    String MESSAGE_TELEPHONE = "请输入正确座机号码！";

    String REGEXP_REGION_CODE = "\\d{6}";
    String MESSAGE_REGION_CODE = "地区编码必须为 6 位数字！";

    String MESSAGE_DETAILED_ADDRESS = "详细地址长度不能超过 100 个字符！";
}
