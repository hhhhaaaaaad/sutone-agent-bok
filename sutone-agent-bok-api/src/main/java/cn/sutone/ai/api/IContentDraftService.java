package cn.sutone.ai.api;

import cn.sutone.ai.api.dto.PageResponseDTO;
import cn.sutone.ai.api.dto.draft.*;
import cn.sutone.ai.api.response.Response;

/**
 * 草稿服务接口
 */
public interface IContentDraftService {

    Response<SaveDraftResponseDTO> saveDraft(SaveDraftRequestDTO requestDTO);

    Response<DraftDetailResponseDTO> queryDraftDetail(Long draftId);

    Response<PageResponseDTO<DraftPageItemResponseDTO>> queryDraftPage(Integer pageNo, Integer pageSize);

    Response<DiscardDraftResponseDTO> discardDraft(Long draftId);
}
