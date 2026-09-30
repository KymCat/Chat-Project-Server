package com.project.ChatProject.attachment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentContentTypeDetectorTest {

    private AttachmentContentTypeDetector detector;

    @BeforeEach
    void setUp() {
        detector = new AttachmentContentTypeDetector();
    }

    @Test
    void detectReturnsPdfForPdfContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "ignored-name.txt",
                "text/plain",
                createPdfBytes()
        );

        String contentType = detector.detect(file);

        assertThat(contentType).isEqualTo("application/pdf");
    }

    @Test
    void detectReturnsTextPlainForTextContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "ignored-name.pdf",
                "application/pdf",
                "일반 텍스트 파일입니다."
                        .getBytes(StandardCharsets.UTF_8)
        );

        String contentType = detector.detect(file);

        assertThat(contentType).isEqualTo("text/plain");
    }

    @Test
    void detectReturnsOctetStreamForBinaryContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "ignored-name.txt",
                "text/plain",
                new byte[]{0x00, 0x01, 0x02, 0x03, 0x04}
        );

        String contentType = detector.detect(file);

        assertThat(contentType)
                .isEqualTo("application/octet-stream");
    }

    private byte[] createPdfBytes() {
        return """
                %PDF-1.4
                1 0 obj
                <<>>
                endobj
                trailer
                <<>>
                %%EOF
                """.getBytes(StandardCharsets.US_ASCII);
    }
}
