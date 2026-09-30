package com.project.ChatProject.attachment;

import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Component
public class AttachmentContentTypeDetector {

    private final Tika tika = new Tika();

    public String detect(MultipartFile file) {
        try(InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream).toLowerCase(Locale.ROOT);
        } catch (IOException exception) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED
            );
        }
    }
}
