package com.feiniao.airobot.service;

import com.feiniao.airobot.model.vo.customerService.DeleteMarkdownFileReqVO;
import com.feiniao.airobot.model.vo.customerService.FindMarkdownFilePageListReqVO;
import com.feiniao.airobot.model.vo.customerService.FindMarkdownFilePageListRspVO;
import com.feiniao.airobot.model.vo.customerService.UpdateMarkdownFileReqVO;
import com.feiniao.airobot.model.vo.chat.PageRspVO;
import com.feiniao.airobot.utils.Response;
import org.springframework.web.multipart.MultipartFile;


//AI客服
public interface CustomerService {
    /**
     * 上传 Markdown 问答文件
     * @param file
     * @return
     */
    Response<?> uploadMarkdownFile(MultipartFile file);

    /**
     * 删除 Markdown 问答文件
     * @param deleteMarkdownFileReqVO
     * @return
     */
    Response<?> deleteMarkdownFile(DeleteMarkdownFileReqVO deleteMarkdownFileReqVO);

    /**
     * 修改 Markdown 问答文件信息
     * @param updateMarkdownFileReqVO
     * @return
     */
    Response<?> updateMarkdownFile(UpdateMarkdownFileReqVO updateMarkdownFileReqVO);

    /**
     * 分页查询 Markdown 问答文件
     * @param findMarkdownFilePageListReqVO
     * @return
     */
    PageRspVO<FindMarkdownFilePageListRspVO> findMarkdownFilePageList(
            FindMarkdownFilePageListReqVO findMarkdownFilePageListReqVO);
}


