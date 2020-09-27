package com.demo.ai.dao;

import com.demo.ai.entity.JdPet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigInteger;
import java.util.List;

/**
 * jd_plantBean(JdPet)表数据库访问层
 *
 * @author makejava
 * @since 2020-09-09 12:02:24
 */
@Mapper
public interface JdPetDao {

    /**
     * 通过ID查询单条数据
     *
     * @param id 主键
     * @return 实例对象
     */
    JdPet queryById(BigInteger id);

    /**
     * 查询指定行数据
     *
     * @param offset 查询起始位置
     * @param limit  查询条数
     * @return 对象列表
     */
    List<JdPet> queryAllByLimit(@Param("offset") int offset, @Param("limit") int limit);


    /**
     * 通过实体作为筛选条件查询
     *
     * @param jdPet 实例对象
     * @return 对象列表
     */
    List<JdPet> queryAll(JdPet jdPet);

    /**
     * 新增数据
     *
     * @param jdPet 实例对象
     * @return 影响行数
     */
    int insert(JdPet jdPet);

    /**
     * 修改数据
     *
     * @param jdPet 实例对象
     * @return 影响行数
     */
    int update(JdPet jdPet);
    int updateAll(JdPet jdPet);

    /**
     * 通过主键删除数据
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteById(BigInteger id);

}