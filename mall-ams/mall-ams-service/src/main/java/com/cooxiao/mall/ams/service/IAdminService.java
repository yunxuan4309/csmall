package com.cooxiao.mall.ams.service;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.admin.dto.AdminAddDTO;
import com.cooxiao.mall.pojo.admin.dto.AdminUpdateDTO;
import com.cooxiao.mall.pojo.admin.vo.AdminVO;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 管理员表 服务类
 * </p>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
public interface IAdminService{
    /**
     * 分页查询
     * @param pageNum
     * @param sizeNum
     * @return
     */
    JsonPage<AdminVO> listAdmins(Integer pageNum, Integer sizeNum);

    /**
     * 新增用户
     * @param adminDTO
     */
    void addAdmin(AdminAddDTO adminDTO);

    /**
     * 模糊查询用户
     * @param pageNum
     * @param sizeNum
     * @param query
     * @return
     */
    JsonPage<AdminVO> queryAdmins(Integer pageNum, Integer sizeNum, String query);

    /**
     * 更新用户
     * @param adminUpdateDTO
     */
    @Transactional
    void updateAdmin(AdminUpdateDTO adminUpdateDTO);

    /**
     * 删除用户
     * @param id
     */
    @Transactional
    void deleteAdmin(Long id);
    AdminVO queryOneAdmin(String username);
}
