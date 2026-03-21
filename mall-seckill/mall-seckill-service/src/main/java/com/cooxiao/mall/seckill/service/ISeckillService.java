package com.cooxiao.mall.seckill.service;

import com.cooxiao.mall.pojo.seckill.dto.SeckillOrderAddDTO;
import com.cooxiao.mall.pojo.seckill.vo.SeckillCommitVO;

public interface ISeckillService {
    SeckillCommitVO commitSeckill(SeckillOrderAddDTO seckillOrderAddDTO);
}
