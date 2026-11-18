package com.dicajakrye.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/** EXIF Orientation 값마다 원래 사진의 왼쪽 위(빨강)가 화면 어디로 가는지 확인 */
class OrientationTest {

    /** 4x2 사진, 왼쪽 위 칸(0,0)만 빨강 */
    private static BufferedImage source() {
        BufferedImage img = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 2; y++) {
                img.setRGB(x, y, Color.BLUE.getRGB());
            }
        }
        img.setRGB(0, 0, Color.RED.getRGB());
        return img;
    }

    private static void assertRedAt(int orientation, int w, int h, int x, int y) {
        BufferedImage out = Orientation.apply(source(), orientation);
        assertThat(out.getWidth()).as("가로 (orientation %d)", orientation).isEqualTo(w);
        assertThat(out.getHeight()).as("세로 (orientation %d)", orientation).isEqualTo(h);
        assertThat(out.getRGB(x, y) & 0xFFFFFF).as("빨강 위치 (orientation %d)", orientation).isEqualTo(0xFF0000);
    }

    @Test
    void 방향값마다_픽셀을_맞게_돌린다() {
        assertRedAt(1, 4, 2, 0, 0); // 그대로
        assertRedAt(2, 4, 2, 3, 0); // 좌우 반전
        assertRedAt(3, 4, 2, 3, 1); // 180도
        assertRedAt(4, 4, 2, 0, 1); // 상하 반전
        assertRedAt(5, 2, 4, 0, 0); // 대각선 반전 (transpose)
        assertRedAt(6, 2, 4, 1, 0); // 시계 방향 90도: 왼쪽 위 → 오른쪽 위
        assertRedAt(7, 2, 4, 1, 3); // 반대 대각선 반전 (transverse)
        assertRedAt(8, 2, 4, 0, 3); // 반시계 90도: 왼쪽 위 → 왼쪽 아래
    }
}
