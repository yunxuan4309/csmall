package com.cooxiao.mall.front.service.impl;

import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.front.service.IFrontCategoryService;
import com.cooxiao.mall.pojo.front.entity.FrontCategoryEntity;
import com.cooxiao.mall.pojo.front.vo.FrontCategoryTreeVO;
import com.cooxiao.mall.pojo.product.vo.CategoryStandardVO;
import com.cooxiao.mall.product.service.front.IForFrontCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * 返回三级分类树
 */
@Service
@Slf4j
public class FrontCategoryServiceImpl implements IFrontCategoryService {
    //该方法的返回值,是外层的灰框;是所有分类的集合
    //红框一级分类 蓝框二级分类 黄框三级分类

    //front模块要dubbo调用product模块的方法,实现查询所有分类信息列表
    @DubboReference
    private IForFrontCategoryService dubboCategoryService;

    //方法要将查询到的分类信息保存到Redis,所以需要操作redis的对象,这里去掉泛型,进行强转,使用范围更宽泛
    @Autowired
    private RedisTemplate redisTemplate;

    //开发时,使用Redis要定义一个常量,作为key的名称,防止编码的拼写错误
    public static final String CATEGORY_TREE_KEY="category_tree";

    //返回三级分类树对象
    //注意:FrontCategoryTreeVO里面只是定义了一个泛型List<T>,FrontCategoryTreeVO灰框
    @Override
    public FrontCategoryTreeVO categoryTree() {
        //方法开始,先检查redis中是否已经包含这个key
        if (redisTemplate.hasKey(CATEGORY_TREE_KEY)){
            //当redis中包含这个key时,从redis中获取对应的值直接返回即可
            FrontCategoryTreeVO<FrontCategoryEntity> treeVO=(FrontCategoryTreeVO<FrontCategoryEntity>)
            redisTemplate.boundValueOps(CATEGORY_TREE_KEY).get();
            // 别忘了返回
            return treeVO;
        }
        //Redis中没有三级分类树信息,表示本次请求可能是第一次访问
        //这样就需要从数据库中查询所有分类信息,构建为三级分类树对象,再保存到Redis
        //利用dubbo调用product模块查询查询所有分类信息的功能
        List<CategoryStandardVO> categoryList=dubboCategoryService.getCategoryList();
        //上面查询到的categoryList就包含了所有的分类信息
        //下面要将分类信息按照界别构建成分类树,需要一个能够包含保存子分类集合属性的类(children属性)
        //FrontCategoryEntity这类就是包含了分类基本属性和子分类集合属性的实体类
        //因为转换过程比较复杂,所以单独编写一个方法实现转换
        FrontCategoryTreeVO<FrontCategoryEntity> treeVO=initTree(categoryList);
        /*2.开始 在完成initTree之后,把三级分类树,放入Redis中*/
        //实际开发中设置时间比较长,例如24小时,甚至更长
        //                                                  时长     24       单位:小时
     //   redisTemplate.boundValueOps(CATEGORY_TREE_KEY).set(treeVO,24, TimeUnit.HOURS);
        //时长 1分钟;实际开发中24小太长
        redisTemplate.boundValueOps(CATEGORY_TREE_KEY).set(treeVO,1, TimeUnit.MINUTES);
        //这里要返回treeVO!!!
        return treeVO;
    }

    private FrontCategoryTreeVO<FrontCategoryEntity> initTree(List<CategoryStandardVO> categoryList) {
        //第一步:
        //确定所有分类对象对应的父分类id
        //创建一个map
        //以父分类id为key,将当前正在遍历的分类对象,保存在对应的value中
        //使用带children的实体
        Map<Long,List<FrontCategoryEntity>> map=new HashMap<>();
        log.info("准备开始构建三级分类树,分类树元素总数:{}",categoryList.size());
        //遍历数据库查询出的所有分类对象集合,并按照具有相同父Id分类放入同一个List中,并存入map中
        for (CategoryStandardVO categoryStandardVO : categoryList) {
            //当前正在遍历的categoryStandardVO对象,没有children属性,
            //需要转换为有children属性的FrontCategoryEntity
            FrontCategoryEntity frontCategoryEntity = new FrontCategoryEntity();
            //将categoryStandardVO 同名属性赋值到frontCategoryEntity对象
            BeanUtils.copyProperties(categoryStandardVO,frontCategoryEntity);
            //因为后面会多次使用到父分类id,所以这里提取出来
            Long parentId=frontCategoryEntity.getParentId();
            // 判断map中是否已经存在这个父分类id作为key的元素
            if (!map.containsKey(parentId)){//注意:也就是parentId=0的情况
                //如果map中没有这个key,表示当前分类对象的父分类id第一次出现
                //要在map中新增个元素,key就是这个父分类id,元素的值是个list要实例化
                List<FrontCategoryEntity> value=new ArrayList<>();
                //将当前分类对象保存到这个list中
                value.add(frontCategoryEntity);
                //最后将key和value组合保存到map
                //注意:如果parentId=0情况,将此种情况的frontCategoryEntity,都放入key=(parentId=0)的value中;
                map.put(parentId,value);

            }else{
                //如果map中已经存在当前分类对象的父分类id为key的元素
                //我们就直接获取这个key的value,在value中添加当前分类对象
                map.get(parentId).add(frontCategoryEntity);
            }
        }

        //第二步:
        //构建三级分类树,将子分类对象集合添加到对应的父分类对象的children属性中
        //先从所有的一级分类对象开始,已经是父分类id为0的对象
        List<FrontCategoryEntity> firstLevels=map.get(0L);
        //判断一级分类集合是否为null(或元素个数为0),抛出异常中止程序
        if (firstLevels==null||firstLevels.isEmpty()){
            throw new CoolSharkServiceException(
                    ResponseCode.INTERNAL_SERVER_ERROR,"没有一级分类对象!");
        }
        //遍历一级分类集合
        for (FrontCategoryEntity oneLevel : firstLevels) {
            //一级分类对象的id,就是二级分类对象父id
            Long secondLevelParentId=oneLevel.getId();//getId()!!!
            //根据二级分类的父Id获取二级分类对象集合
            List<FrontCategoryEntity> secondLevels=map.get(secondLevelParentId);
            //判断二级分类集合是否为null
            if (secondLevels==null||secondLevels.isEmpty()){
                //二级分类集合缺失,不用抛异常,只需在日志输出警告即可
                log.warn("当前二级分类没有内容:{}",secondLevelParentId);
                //当前二级分类没有内容,无序运行循环后面的语句,直接进行下次循环
                continue;
            }
            //二级分类集合确实有元素,可以开始遍历二级分类集合
            //注意:主要功能将三级children赋给二级children属性
            for (FrontCategoryEntity twoLevel : secondLevels) {
                //获取当前二级分类的id,作为三级分类的父id
                Long thirdLevelParentId=twoLevel.getId();//getId()!!!
                //根据这个thirdLevelParentId获取所有三级分类对象集合
                List<FrontCategoryEntity> thirdLevels=map.get(thirdLevelParentId);
                if (thirdLevels==null||thirdLevels.isEmpty()){
                    log.warn("当前二级分类没有三级分类:{}",thirdLevelParentId);
                    continue;
                }
                //将三级分类集合至少有一个元素,将它赋给二级分类的children属性
                twoLevel.setChildrens(thirdLevels);
            }
            //在内层循环结束后,在外层循环结束前
            //将二级分类结合赋值给一级分类对象的children属性
            oneLevel.setChildrens(secondLevels);
        }
        //循环结束后,我们所有的分类对象都已经保存在了自己对应的父分类对象的children属性中了
        //但是我们最终返回的是FrontCategoryTreeVO类型中返回
        FrontCategoryTreeVO<FrontCategoryEntity> treeVO=new FrontCategoryTreeVO<>();
        //注意:返回一级分类即可(因为每个一级分类的下级分类都在childrens List当中)
        treeVO.setCategories(firstLevels);
        // 最后千万别忘了返回treeVO
        return treeVO;
    }
    //todo 下节课 验证三级分类树,以及sso 登录验证
}
