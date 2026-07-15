package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class FileUploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    @PostMapping("files/upload")
    public Response<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "文件不能为空");
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "文件大小不能超过 5MB");
            }

            String originalFilename = file.getOriginalFilename();
            String extension = getExtension(originalFilename);
            if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "仅支持 jpg、png、gif、webp、svg 格式的图片");
            }

            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String storedFilename = UUID.randomUUID().toString() + "." + extension.toLowerCase();
            Path targetPath = uploadPath.resolve(storedFilename);
            file.transferTo(targetPath.toFile());

            String url = "/uploads/" + storedFilename;
            log.info("文件上传成功: {} -> {}", originalFilename, url);

            return Response.<Map<String, String>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("url", url, "filename", storedFilename))
                    .build();
        } catch (AppException e) {
            log.error("文件上传失败", e);
            return fail(e);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return fail(e);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
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
