package com.dicajakrye.commons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dicajakrye.image.ImageProcessor;
import com.dicajakrye.image.ImageProcessor.ImageRejectedException;
import org.junit.jupiter.api.Test;

class ImageProcessorTest {

    final ImageProcessor processor = new ImageProcessor();

    @Test
    void 큰_사진은_보기용_1600_썸네일_640으로_줄인다() {
        var p = processor.process(TestImages.jpeg(3072, 2304));
        assertThat(TestImages.size(p.display())).containsExactly(1600, 1200);
        assertThat(TestImages.size(p.thumbnail())).containsExactly(640, 480);
        assertThat(TestImages.hasExif(p.display())).isFalse();
        assertThat(TestImages.hasExif(p.thumbnail())).isFalse();
    }

    @Test
    void 작은_사진은_늘리지_않는다() {
        var p = processor.process(TestImages.jpeg(800, 600));
        assertThat(TestImages.size(p.display())).containsExactly(800, 600);
        assertThat(TestImages.size(p.thumbnail())).containsExactly(640, 480);
    }

    @Test
    void 이미지가_아니면_거부한다() {
        assertThatThrownBy(() -> processor.process("not an image".getBytes()))
                .isInstanceOf(ImageRejectedException.class);
    }
}
