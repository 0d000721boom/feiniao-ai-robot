package com.feiniao.airobot.domain.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feiniao.airobot.domain.dos.AiCustomerServiceFileStorageDO;

/**
 * @Description: TODO
 **/
public interface AiCustomerServiceFileStorageMapper extends BaseMapper<AiCustomerServiceFileStorageDO> {

    /**
     * 分页查询
     * @param current
     * @param size
     * @return
     */
    default Page<AiCustomerServiceFileStorageDO> selectPageList(Long current, Long size) {
        // 分页对象(查询第几页、每页多少数据)
        Page<AiCustomerServiceFileStorageDO> page = new Page<>(current, size);

        // 构建查询条件
        LambdaQueryWrapper<AiCustomerServiceFileStorageDO> wrapper = Wrappers.<AiCustomerServiceFileStorageDO>lambdaQuery()
                .orderByDesc(AiCustomerServiceFileStorageDO::getCreateTime); // 按创建时间倒序

        return selectPage(page, wrapper);
    }

    /**
     * 根据文件 MD5 值查询
     * @param fileMd5
     * @return
     */
    default AiCustomerServiceFileStorageDO selectByMd5(String fileMd5) {
        return selectOne(Wrappers.<AiCustomerServiceFileStorageDO>lambdaQuery()
                .eq(AiCustomerServiceFileStorageDO::getFileMd5, fileMd5));
    }

}
