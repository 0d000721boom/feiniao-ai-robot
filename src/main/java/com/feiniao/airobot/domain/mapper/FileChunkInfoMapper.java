package com.feiniao.airobot.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.feiniao.airobot.domain.dos.FileChunkInfoDO;

import java.util.List;

/**
 * @Author: 犬小哈
 * @Date: 2025/8/11 11:36
 * @Version: v1.0.0
 * @Description: 分片信息表
 **/
public interface FileChunkInfoMapper extends BaseMapper<FileChunkInfoDO> {

    default FileChunkInfoDO selectByFileMd5AndChunkNumber(String fileMd5, Integer chunkNumber) {
        return selectOne(
                Wrappers.<FileChunkInfoDO>lambdaQuery()
                        .eq(FileChunkInfoDO::getFileMd5, fileMd5)
                        .eq(FileChunkInfoDO::getChunkNumber, chunkNumber)
        );
    }

    /**
     * 根据文件 MD5 值查询所有已上传的分片
     * @param fileMd5
     * @return
     */
    default List<FileChunkInfoDO> selectChunkedList(String fileMd5) {
        return selectList(
                Wrappers.<FileChunkInfoDO>lambdaQuery()
                        .eq(FileChunkInfoDO::getFileMd5, fileMd5)
                        .orderByAsc(FileChunkInfoDO::getChunkNumber)
        );
    }

    default int deleteByFileMd5(String fileMd5) {
        return delete(
                Wrappers.<FileChunkInfoDO>lambdaQuery()
                        .eq(FileChunkInfoDO::getFileMd5, fileMd5)
        );
    }

}
