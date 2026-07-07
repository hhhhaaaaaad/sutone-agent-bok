package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.IContentDraftService;
import cn.sutone.ai.api.dto.PageResponseDTO;
import cn.sutone.ai.api.dto.draft.*;
import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.service.IDraftDomainService;
import cn.sutone.ai.domain.content.service.command.SaveDraftCommand;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 草稿 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class ContentDraftController implements IContentDraftService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Long DEFAULT_USER_ID = 1L;

    @Resource
    private IDraftDomainService draftDomainService;

    @PostMapping("drafts/save")
    @Override
    public Response<SaveDraftResponseDTO> saveDraft(@RequestBody SaveDraftRequestDTO requestDTO) {
        try {
            SaveDraftCommand command = SaveDraftCommand.builder()
                    .draftId(requestDTO.getDraftId())
                    .title(requestDTO.getTitle())
                    .contentMd(requestDTO.getContentMd())
                    .summary(requestDTO.getSummary())
                    .coverUrl(requestDTO.getCoverUrl())
                    .build();

            DraftEntity draft = draftDomainService.saveDraft(DEFAULT_USER_ID, command);

            return Response.<SaveDraftResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(SaveDraftResponseDTO.builder()
                            .draftId(draft.getDraftId())
                            .title(draft.getTitle())
                            .status(draft.getStatus().getCode())
                            .statusDesc(draft.getStatus().getDesc())
                            .lastUpdateTime(draft.getUpdateTime() != null ? draft.getUpdateTime().format(DTF) : null)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("保存草稿失败", e);
            return fail(e);
        }
    }

    @GetMapping("drafts/{draftId}")
    @Override
    public Response<DraftDetailResponseDTO> queryDraftDetail(@PathVariable Long draftId) {
        try {
            DraftEntity draft = draftDomainService.queryDraftDetail(draftId, DEFAULT_USER_ID);
            return Response.<DraftDetailResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toDetailDTO(draft))
                    .build();
        } catch (Exception e) {
            log.error("查询草稿详情失败 draftId:{}", draftId, e);
            return fail(e);
        }
    }

    @GetMapping("drafts/page")
    @Override
    public Response<PageResponseDTO<DraftPageItemResponseDTO>> queryDraftPage(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            List<DraftEntity> drafts = draftDomainService.queryDraftPage(DEFAULT_USER_ID, pageNo, pageSize);
            List<DraftPageItemResponseDTO> items = drafts.stream()
                    .map(this::toPageItemDTO)
                    .toList();

            return Response.<PageResponseDTO<DraftPageItemResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(PageResponseDTO.<DraftPageItemResponseDTO>builder()
                            .total(items.size())
                            .pageNo(pageNo)
                            .pageSize(pageSize)
                            .list(items)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("查询草稿列表失败", e);
            return fail(e);
        }
    }

    @PostMapping("drafts/{draftId}/discard")
    @Override
    public Response<DiscardDraftResponseDTO> discardDraft(@PathVariable Long draftId) {
        try {
            DraftEntity draft = draftDomainService.discardDraft(draftId, DEFAULT_USER_ID);
            return Response.<DiscardDraftResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(DiscardDraftResponseDTO.builder()
                            .draftId(draft.getDraftId())
                            .status(draft.getStatus().getCode())
                            .statusDesc(draft.getStatus().getDesc())
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("废弃草稿失败 draftId:{}", draftId, e);
            return fail(e);
        }
    }

    private DraftDetailResponseDTO toDetailDTO(DraftEntity draft) {
        return DraftDetailResponseDTO.builder()
                .draftId(draft.getDraftId())
                .userId(draft.getUserId())
                .title(draft.getTitle())
                .contentMd(draft.getContentMd())
                .summary(draft.getSummary())
                .coverUrl(draft.getCoverUrl())
                .status(draft.getStatus().getCode())
                .statusDesc(draft.getStatus().getDesc())
                .createTime(draft.getCreateTime() != null ? draft.getCreateTime().format(DTF) : null)
                .updateTime(draft.getUpdateTime() != null ? draft.getUpdateTime().format(DTF) : null)
                .build();
    }

    private DraftPageItemResponseDTO toPageItemDTO(DraftEntity draft) {
        return DraftPageItemResponseDTO.builder()
                .draftId(draft.getDraftId())
                .title(draft.getTitle())
                .summary(draft.getSummary())
                .coverUrl(draft.getCoverUrl())
                .status(draft.getStatus().getCode())
                .statusDesc(draft.getStatus().getDesc())
                .updateTime(draft.getUpdateTime() != null ? draft.getUpdateTime().format(DTF) : null)
                .build();
    }

    private <T> Response<T> fail(Exception e) {
        String code = ResponseCode.UN_ERROR.getCode();
        String info = e.getMessage();
        if (e instanceof AppException ae) {
            code = ae.getCode() != null ? ae.getCode() : code;
            info = ae.getInfo() != null ? ae.getInfo() : info;
        }
        return Response.<T>builder().code(code).info(info).build();
    }
}
